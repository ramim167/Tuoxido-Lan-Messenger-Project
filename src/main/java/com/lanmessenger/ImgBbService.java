package com.lanmessenger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ImgBbService {
    private static final String API_URL = "https://api.imgbb.com/1/upload";
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private ImgBbService() {}

    public static UploadResult uploadImage(byte[] data) throws Exception {
        return uploadImage(data, null);
    }

    public static UploadResult uploadImage(byte[] data, Integer expirationSeconds) throws Exception {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Image data is empty.");
        }

        String base64Image = Base64.getEncoder().encodeToString(data);
        StringBuilder formData = new StringBuilder()
                .append("key=").append(AppConfig.getImgBbApiKey())
                .append("&image=").append(URLEncoder.encode(base64Image, StandardCharsets.UTF_8));

        if (expirationSeconds != null && expirationSeconds > 0) {
            formData.append("&expiration=").append(expirationSeconds);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData.toString()))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null || !root.has("success") || !root.get("success").getAsBoolean()) {
            throw new IllegalStateException("ImgBB upload failed.");
        }

        JsonObject payload = root.getAsJsonObject("data");
        if (payload == null || !payload.has("url")) {
            throw new IllegalStateException("ImgBB did not return an image URL.");
        }

        String imageUrl = payload.get("url").getAsString();
        String deleteUrl = payload.has("delete_url") ? payload.get("delete_url").getAsString() : null;
        return new UploadResult(imageUrl, deleteUrl);
    }

    public static final class UploadResult {
        public final String imageUrl;
        public final String deleteUrl;

        public UploadResult(String imageUrl, String deleteUrl) {
            this.imageUrl = imageUrl;
            this.deleteUrl = deleteUrl;
        }
    }
}
