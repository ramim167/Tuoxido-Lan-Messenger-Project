package com.lanmessenger;

public final class FirebaseConfig {

    private FirebaseConfig() {}

    public static String getApiKey() {
        return AppConfig.getFirebaseApiKey();
    }

    public static String getSignUp() {
        return "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + getApiKey();
    }

    public static String getSignIn() {
        return "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + getApiKey();
    }

    public static String getLookup() {
        return "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + getApiKey();
    }

    public static String getSendOobCode() {
        return "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + getApiKey();
    }
}
