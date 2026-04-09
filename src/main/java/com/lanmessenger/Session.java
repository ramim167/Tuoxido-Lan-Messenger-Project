

package com.lanmessenger;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public final class Session {
    private Session() {}

    private static FirebaseAuthService.AuthResult auth;
    private static UserProfile profile;
    private static boolean autoLoggedIn = false;

    private static final Preferences prefs = Preferences.userNodeForPackage(Session.class);
    private static final String KEY_EMAIL = "saved_user_email";
    private static final String KEY_LOCAL_ID = "saved_local_id";
    private static final String KEY_EMAIL_HISTORY = "saved_email_history";
    private static final int MAX_EMAIL_HISTORY = 6;

    public static void setAuth(FirebaseAuthService.AuthResult a) {
        auth = a;
    }

    public static void rememberAuth(FirebaseAuthService.AuthResult a) {
        if (a == null || !a.ok || a.email == null) {
            clearRememberedAuth();
            return;
        }

        prefs.put(KEY_EMAIL, a.email);
        rememberEmailSuggestion(a.email);
        if (a.localId != null) {
            prefs.put(KEY_LOCAL_ID, a.localId);
        } else {
            prefs.remove(KEY_LOCAL_ID);
        }
    }

    public static FirebaseAuthService.AuthResult getAuth() { return auth; }

    public static void setProfile(UserProfile p) { profile = p; }
    public static UserProfile getProfile() { return profile; }

    public static void setAutoLoggedIn(boolean val) { autoLoggedIn = val; }

    public static boolean isLoggedIn() { return (auth != null && auth.ok) || autoLoggedIn; }

    public static void clear() {
        auth = null;
        profile = null;
        autoLoggedIn = false;

        clearRememberedAuth();
    }

    public static String getSavedEmail() { return prefs.get(KEY_EMAIL, null); }
    public static String getSavedLocalId() { return prefs.get(KEY_LOCAL_ID, null); }

    public static void rememberEmailSuggestion(String email) {
        if (email == null) return;
        String normalized = email.trim().toLowerCase();
        if (normalized.isEmpty()) return;

        List<String> history = new ArrayList<>(getSavedEmailSuggestions());
        history.removeIf(saved -> normalized.equalsIgnoreCase(saved));
        history.add(0, normalized);
        if (history.size() > MAX_EMAIL_HISTORY) {
            history = new ArrayList<>(history.subList(0, MAX_EMAIL_HISTORY));
        }
        prefs.put(KEY_EMAIL_HISTORY, String.join("\n", history));
    }

    public static List<String> getSavedEmailSuggestions() {
        String raw = prefs.get(KEY_EMAIL_HISTORY, "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String email : raw.split("\\R")) {
            if (email != null && !email.isBlank()) {
                out.add(email.trim());
            }
        }
        return out;
    }

    public static void clearRememberedAuth() {
        prefs.remove(KEY_EMAIL);
        prefs.remove(KEY_LOCAL_ID);
    }
}
