package com.lanmessenger;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoDatabaseService {

    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static void connect() {
        try {
            if (mongoClient == null) {
                mongoClient = MongoClients.create(AppConfig.getMongoConnectionString());
                database = mongoClient.getDatabase(AppConfig.getMongoDatabaseName());

                System.out.println(GREEN + "✅ Successfully connected to MongoDB Atlas!" + RESET);

                MessageService.setupAutoDelete();
                MomentsService.setupAutoDelete();
            }
        } catch (Exception e) {
            System.err.println(RED + "❌ Failed to connect to MongoDB: " + e.getMessage() + RESET);
        }
    }

    public static MongoDatabase getDatabase() {
        if (database == null) {
            connect();
        }
        return database;
    }
}