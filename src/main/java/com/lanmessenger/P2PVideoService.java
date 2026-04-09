package com.lanmessenger;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class P2PVideoService {
    private static final int PORT = 9090;
    private static final int FRAME_INTERVAL_MS = 35;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final Path CAMERA_LOCK_PATH = Path.of(System.getProperty("java.io.tmpdir"), "tuoxido-camera.lock");

    private static Webcam webcam;
    private static volatile boolean isRunning = false;
    private static volatile boolean videoEnabled = true;
    private static volatile boolean localCameraAvailable = false;
    private static volatile Runnable firstFrameCallback = null;
    private static Socket socket;
    private static ServerSocket serverSocket;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static FileChannel cameraLockChannel;
    private static FileLock cameraLock;

    private P2PVideoService() {
    }

    public static synchronized void startServer(ImageView remoteView, Runnable onFirstFrame) {
        if (isRunning) {
            stopCall();
        }
        isRunning = true;
        videoEnabled = true;
        localCameraAvailable = false;
        firstFrameCallback = onFirstFrame;

        Thread t = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);
                socket = serverSocket.accept();
                handleConnection(socket, remoteView);
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
                stopCall();
            }
        }, "p2p-video-server");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void startClient(String serverIp, ImageView remoteView, Runnable onFirstFrame) {
        if (isRunning) {
            stopCall();
        }
        isRunning = true;
        videoEnabled = true;
        localCameraAvailable = false;
        firstFrameCallback = onFirstFrame;

        Thread t = new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, PORT), CONNECT_TIMEOUT_MS);
                handleConnection(socket, remoteView);
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
                stopCall();
            }
        }, "p2p-video-client");
        t.setDaemon(true);
        t.start();
    }

    private static void handleConnection(Socket s, ImageView remoteView) {
        if (remoteView == null) {
            stopCall();
            return;
        }

        try {
            ensureWebcam();
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        } catch (Exception e) {
            e.printStackTrace();
            stopCall();
            return;
        }

        Thread sender = new Thread(() -> {
            try {
                while (isRunning && out != null) {
                    if (localCameraAvailable && webcam != null && webcam.isOpen() && videoEnabled) {
                        java.awt.image.BufferedImage image = webcam.getImage();
                        if (image != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(image, "jpg", baos);
                            byte[] bytes = baos.toByteArray();
                            out.writeInt(bytes.length);
                            out.write(bytes);
                            out.flush();
                        } else {
                            out.writeInt(0);
                            out.flush();
                        }
                    } else {
                        out.writeInt(0);
                        out.flush();
                    }
                    Thread.sleep(FRAME_INTERVAL_MS);
                }
            } catch (Exception e) {
                stopCall();
            }
        }, "p2p-video-send");
        sender.setDaemon(true);
        sender.start();

        Thread receiver = new Thread(() -> {
            try {
                while (isRunning && in != null) {
                    int len = in.readInt();
                    if (len <= 0) {
                        continue;
                    }

                    byte[] bytes = new byte[len];
                    in.readFully(bytes);
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    Image img = new Image(bais);

                    if (firstFrameCallback != null) {
                        firstFrameCallback.run();
                        firstFrameCallback = null;
                    }

                    Platform.runLater(() -> remoteView.setImage(img));
                }
            } catch (Exception e) {
                stopCall();
            }
        }, "p2p-video-recv");
        receiver.setDaemon(true);
        receiver.start();
    }

    public static synchronized void stopCall() {
        isRunning = false;
        videoEnabled = true;
        localCameraAvailable = false;
        firstFrameCallback = null;

        closeQuietly(out);
        out = null;
        closeQuietly(in);
        in = null;
        closeQuietly(socket);
        socket = null;
        closeQuietly(serverSocket);
        serverSocket = null;

        try {
            if (webcam != null) {
                webcam.close();
            }
        } catch (Exception ignored) {
        } finally {
            webcam = null;
            releaseCameraLock();
        }
    }

    private static void ensureWebcam() {
        if (webcam != null && webcam.isOpen()) {
            localCameraAvailable = true;
            return;
        }

        if (!acquireCameraLock()) {
            localCameraAvailable = false;
            videoEnabled = false;
            return;
        }

        try {
            List<Webcam> webcams = Webcam.getWebcams();
            if (webcams == null || webcams.isEmpty()) {
                releaseCameraLock();
                localCameraAvailable = false;
                videoEnabled = false;
                return;
            }

            for (Webcam candidate : webcams) {
                if (candidate == null) {
                    continue;
                }
                try {
                    configureViewSize(candidate);
                    candidate.open();
                    webcam = candidate;
                    localCameraAvailable = true;
                    return;
                } catch (Exception ignored) {
                    try {
                        candidate.close();
                    } catch (Exception ignoredAgain) {
                    }
                }
            }

            releaseCameraLock();
            localCameraAvailable = false;
            videoEnabled = false;
        } catch (Exception e) {
            releaseCameraLock();
            localCameraAvailable = false;
            videoEnabled = false;
        }
    }

    private static void configureViewSize(Webcam candidate) {
        try {
            Dimension[] sizes = candidate.getViewSizes();
            if (sizes == null || sizes.length == 0) {
                return;
            }

            for (Dimension size : sizes) {
                if (size.width == 640 && size.height == 480) {
                    candidate.setViewSize(size);
                    return;
                }
            }

            candidate.setViewSize(sizes[0]);
        } catch (Exception ignored) {
        }
    }

    private static boolean acquireCameraLock() {
        try {
            if (cameraLock != null && cameraLock.isValid()) {
                return true;
            }

            cameraLockChannel = FileChannel.open(
                    CAMERA_LOCK_PATH,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );
            cameraLock = cameraLockChannel.tryLock();
            return cameraLock != null && cameraLock.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    private static void releaseCameraLock() {
        try {
            if (cameraLock != null && cameraLock.isValid()) {
                cameraLock.release();
            }
        } catch (Exception ignored) {
        } finally {
            cameraLock = null;
            closeQuietly(cameraLockChannel);
            cameraLockChannel = null;
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    public static void setVideoEnabled(boolean enabled) {
        videoEnabled = enabled && localCameraAvailable;
    }

    public static boolean isLocalCameraAvailable() {
        return localCameraAvailable;
    }
}
