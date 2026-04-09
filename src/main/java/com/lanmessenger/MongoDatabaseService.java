package com.lanmessenger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoDatabaseService {

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static void connect() {
        try {
            if (mongoClient == null) {
                mongoClient = MongoClients.create(AppConfig.getMongoConnectionString());
                database = mongoClient.getDatabase(AppConfig.getMongoDatabaseName());
                System.out.println("✅ Successfully connected to MongoDB Atlas!");

                MessageService.setupAutoDelete();
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to MongoDB: " + e.getMessage());
        }
    }

    public static MongoDatabase getDatabase() {
        if (database == null) {
            connect();
        }
        return database;
    }
}
