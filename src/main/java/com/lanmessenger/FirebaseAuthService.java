package com.lanmessenger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class FirebaseAuthService {

    private final HttpClient http = HttpClient.newHttpClient();

    public static final class AuthResult {
        public final boolean ok;
        public final String message;

        public final String email;
        public final String idToken;
        public final String refreshToken;
        public final String localId;

        private AuthResult(boolean ok, String message, String email, String idToken, String refreshToken, String localId) {
            this.ok = ok;
            this.message = message;
            this.email = email;
            this.idToken = idToken;
            this.refreshToken = refreshToken;
            this.localId = localId;
        }

        public static AuthResult success(String email, String idToken, String refreshToken, String localId) {
            return new AuthResult(true, "OK", email, idToken, refreshToken, localId);
        }

        public static AuthResult fail(String msg) {
            return new AuthResult(false, msg, null, null, null, null);
        }
    }

    public AuthResult signUp(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("returnSecureToken", true);

        String res = postJson(FirebaseConfig.getSignUp(), body.toString());
        if (res == null) return AuthResult.fail("Network error");

        JsonObject jo = JsonParser.parseString(res).getAsJsonObject();
        if (jo.has("error")) return AuthResult.fail(readFirebaseError(jo));

        String idToken = getStr(jo, "idToken");
        String refreshToken = getStr(jo, "refreshToken");
        String localId = getStr(jo, "localId");
        String outEmail = getStr(jo, "email");

        return AuthResult.success(outEmail, idToken, refreshToken, localId);
    }

    public AuthResult signIn(String email, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("returnSecureToken", true);

        String res = postJson(FirebaseConfig.getSignIn(), body.toString());
        if (res == null) return AuthResult.fail("Network error");

        JsonObject jo = JsonParser.parseString(res).getAsJsonObject();
        if (jo.has("error")) return AuthResult.fail(readFirebaseError(jo));

        String idToken = getStr(jo, "idToken");
        String refreshToken = getStr(jo, "refreshToken");
        String localId = getStr(jo, "localId");
        String outEmail = getStr(jo, "email");

        return AuthResult.success(outEmail, idToken, refreshToken, localId);
    }

    public AuthResult sendEmailVerification(String idToken) {
        JsonObject body = new JsonObject();
        body.addProperty("requestType", "VERIFY_EMAIL");
        body.addProperty("idToken", idToken);

        String res = postJson(FirebaseConfig.getSendOobCode(), body.toString());
        if (res == null) return AuthResult.fail("Network error");

        JsonObject jo = JsonParser.parseString(res).getAsJsonObject();
        if (jo.has("error")) return AuthResult.fail(readFirebaseError(jo));

        return new AuthResult(true, "Verification email sent", getStr(jo, "email"), null, null, null);
    }
    public AuthResult sendPasswordResetEmail(String email) {
        JsonObject body = new JsonObject();
        body.addProperty("requestType", "PASSWORD_RESET");
        body.addProperty("email", email);

        String res = postJson(FirebaseConfig.getSendOobCode(), body.toString());
        if (res == null) return AuthResult.fail("Network error");

        JsonObject jo = JsonParser.parseString(res).getAsJsonObject();
        if (jo.has("error")) return AuthResult.fail(readFirebaseError(jo));

        return new AuthResult(true, "Password reset email sent", getStr(jo, "email"), null, null, null);
    }

    public boolean isEmailVerified(String idToken) {
        JsonObject body = new JsonObject();
        body.addProperty("idToken", idToken);

        String res = postJson(FirebaseConfig.getLookup(), body.toString());
        if (res == null) return false;

        JsonObject jo = JsonParser.parseString(res).getAsJsonObject();
        if (jo.has("error")) return false;

        JsonArray users = jo.getAsJsonArray("users");
        if (users == null || users.size() == 0) return false;

        JsonObject u0 = users.get(0).getAsJsonObject();
        return u0.has("emailVerified") && u0.get("emailVerified").getAsBoolean();
    }

    private String postJson(String url, String jsonBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.body();
        } catch (IOException | InterruptedException e) {
            return null;
        }
    }

    private static String getStr(JsonObject jo, String key) {
        return jo.has(key) ? jo.get(key).getAsString() : null;
    }

    private static String readFirebaseError(JsonObject root) {

        try {
            JsonObject err = root.getAsJsonObject("error");
            String code = err.has("message") ? err.get("message").getAsString() : "UNKNOWN_ERROR";

            return switch (code) {
                case "EMAIL_EXISTS" -> "This email is already registered. Please login.";
                case "OPERATION_NOT_ALLOWED" -> "Email/Password sign-in is not enabled in Firebase.";
                case "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Try again later.";
                case "EMAIL_NOT_FOUND" -> "User not found. Please double-check your mail.";
                case "INVALID_PASSWORD" -> "Wrong password.";
                case "USER_DISABLED" -> "This user account has been disabled.";
                case "INVALID_EMAIL" -> "Invalid email address.";
                case "INVALID_LOGIN_CREDENTIALS" -> "Incorrect email or password. Please try again.";

                case "WEAK_PASSWORD : Password should be at least 6 characters" -> "Password too weak (min 6 chars).";
                default -> "Firebase error: " + code;
            };
        } catch (Exception e) {
            return "Firebase error";
        }
    }
}
