package com.lanmessenger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public final class P2PAudioService {
    private static final int PORT = 9091;
    private static final int BUFFER_SIZE = 4096;
    private static final int CONNECT_TIMEOUT_MS = 4000;

    private static volatile boolean isRunning = false;
    private static volatile boolean micEnabled = true;
    private static volatile Runnable firstAudioCallback = null;

    private static Socket socket;
    private static ServerSocket serverSocket;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static TargetDataLine micLine;
    private static SourceDataLine speakerLine;
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    private P2PAudioService() {
    }

    public static synchronized void startServer(Runnable onFirstAudio) {
        if (isRunning) {
            stop();
        }
        isRunning = true;
        firstAudioCallback = onFirstAudio;
        Thread t = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverSocket.setReuseAddress(true);
                socket = serverSocket.accept();
                openStreams();
                startPipelines();
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
                stop();
            }
        }, "p2p-audio-server");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void startClient(String host, Runnable onFirstAudio) {
        if (isRunning) {
            stop();
        }
        isRunning = true;
        firstAudioCallback = onFirstAudio;
        Thread t = new Thread(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
                openStreams();
                startPipelines();
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
                stop();
            }
        }, "p2p-audio-client");
        t.setDaemon(true);
        t.start();
    }

    public static void setMicEnabled(boolean enabled) {
        micEnabled = enabled;
    }

    private static void openStreams() throws Exception {
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());

        try {
            micLine = AudioSystem.getTargetDataLine(FORMAT);
            micLine.open(FORMAT);
            micLine.start();
        } catch (Exception e) {
            micLine = null;
            System.err.println("Mic not available; audio will be receive-only.");
        }

        speakerLine = AudioSystem.getSourceDataLine(FORMAT);
        speakerLine.open(FORMAT);
        speakerLine.start();
    }

    private static void startPipelines() {
        Thread send = new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            try {
                while (isRunning) {
                    if (micLine != null && micEnabled) {
                        int read = micLine.read(buf, 0, buf.length);
                        if (read > 0) {
                            out.writeInt(read);
                            out.write(buf, 0, read);
                            out.flush();
                        }
                    } else {
                        out.writeInt(0);
                        out.flush();
                        Thread.sleep(80);
                    }
                }
            } catch (Exception e) {
                stop();
            }
        }, "p2p-audio-send");
        send.setDaemon(true);
        send.start();

        Thread recv = new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            try {
                while (isRunning) {
                    int len = in.readInt();
                    if (len > 0 && len <= buf.length) {
                        in.readFully(buf, 0, len);
                        if (firstAudioCallback != null) {
                            firstAudioCallback.run();
                            firstAudioCallback = null;
                        }
                        speakerLine.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                stop();
            }
        }, "p2p-audio-recv");
        recv.setDaemon(true);
        recv.start();
    }

    public static synchronized void stop() {
        isRunning = false;
        closeQuietly(micLine);
        closeQuietly(speakerLine);
        closeQuietly(out);
        closeQuietly(in);
        closeQuietly(socket);
        closeQuietly(serverSocket);
        micLine = null;
        speakerLine = null;
        out = null;
        in = null;
        socket = null;
        serverSocket = null;
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

    private static void closeQuietly(Line line) {
        if (line == null) {
            return;
        }
        try {
            line.close();
        } catch (Exception ignored) {
        }
    }
}
