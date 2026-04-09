package com.lanmessenger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                throw new ExceptionInInitializerError(
                    "config.properties not found on classpath. " +
                    "Copy config.properties.example to src/main/resources/config.properties " +
                    "and fill in your credentials."
                );
            }
            PROPS.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load config.properties: " + e.getMessage());
        }
    }

    private AppConfig() {}

    public static String get(String key) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return value;
    }

    public static String getMongoConnectionString() { return get("mongodb.connection_string"); }
    public static String getMongoDatabaseName()     { return get("mongodb.database_name"); }
    public static String getFirebaseApiKey()        { return get("firebase.api_key"); }
    public static String getImgBbApiKey()           { return get("imgbb.api_key"); }
}
