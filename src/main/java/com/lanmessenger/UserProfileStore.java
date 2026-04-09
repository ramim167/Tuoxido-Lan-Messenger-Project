package com.lanmessenger;

import java.time.LocalDate;
import java.util.prefs.Preferences;

public final class UserProfileStore {

    private static final Preferences PREFS = Preferences.userRoot().node("lanmessenger_profiles");

    private UserProfileStore() {}

    private static String k(String localId, String field) {
        return localId + "." + field;
    }

    public static void saveIdentity(String localId, String name, String email) {
        if (localId == null) return;
        if (name != null)  PREFS.put(k(localId, "name"), name);
        if (email != null) PREFS.put(k(localId, "email"), email);
    }

    public static UserProfile loadOrCreate(String localId, String fallbackEmail) {
        if (localId == null || localId.isBlank()) return null;

        String name = PREFS.get(k(localId, "name"), "");
        String email = PREFS.get(k(localId, "email"), fallbackEmail == null ? "" : fallbackEmail);

        String username = PREFS.get(k(localId, "username"), "");
        if (username == null || username.isBlank()) {
            username = generateUsername(localId);
            PREFS.put(k(localId, "username"), username);
        }

        LocalDate birthdate = null;
        String bd = PREFS.get(k(localId, "birthdate"), "");
        if (bd != null && !bd.isBlank()) {
            try { birthdate = LocalDate.parse(bd); } catch (Exception ignored) {}
        }
        String profilePic = PREFS.get(k(localId, "profilePic"), "default_avatar.png");
        return new UserProfile(localId, name, email, username, birthdate,profilePic);
    }

    public static void updateName(String localId, String name) {
        if (localId == null) return;
        if (name == null) name = "";
        PREFS.put(k(localId, "name"), name.trim());
    }
    public static void updateUsername(String localId, String username) {
        if (localId == null) return;
        PREFS.put(k(localId, "username"), username == null ? "" : username.trim());
    }

    public static void updateBirthdate(String localId, LocalDate birthdate) {
        if (localId == null) return;
        if (birthdate == null) PREFS.remove(k(localId, "birthdate"));
        else PREFS.put(k(localId, "birthdate"), birthdate.toString());
    }
    public static void updateProfilePic(String localId, String profilePic) {
        if (localId == null) return;
        PREFS.put(k(localId, "profilePic"), profilePic == null ? "default_avatar.png" : profilePic);
    }
    private static String generateUsername(String localId) {
        String base = localId.replaceAll("[^A-Za-z0-9]", "");
        if (base.length() >= 6) base = base.substring(0, 6).toLowerCase();
        else base = (base + "user00").substring(0, 6).toLowerCase();
        return "lm_" + base;
    }
}
