package com.lanmessenger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MomentsService {

    private static MongoCollection<Document> getMomentsCollection() {
        MongoDatabase database = MongoDatabaseService.getDatabase();
        return database.getCollection("moments");
    }

    public static void createMoment(String username, String text, String imageUrl) {
        try {
            Document doc = new Document("username", username)
                    .append("text", text == null ? "" : text)
                    .append("imageUrl", imageUrl)
                    .append("timestamp", System.currentTimeMillis())
                    .append("likes", new ArrayList<String>())
                    .append("comments", new ArrayList<Document>());

            getMomentsCollection().insertOne(doc);
        } catch (Exception e) {
            System.err.println("âŒ Failed to create moment: " + e.getMessage());
        }
    }

    public static List<Document> getFeed(String myUsername, List<String> friendUsernames) {
        List<Document> feed = new ArrayList<>();
        try {
            Set<String> users = new HashSet<>();
            if (friendUsernames != null) {
                users.addAll(friendUsernames);
            }
            if (myUsername != null) {
                users.add(myUsername);
            }
            if (users.isEmpty()) {
                return feed;
            }

            getMomentsCollection()
                    .find(Filters.in("username", users))
                    .sort(Sorts.descending("timestamp"))
                    .into(feed);
        } catch (Exception e) {
            System.err.println("âŒ Failed to load moments feed: " + e.getMessage());
        }
        return feed;
    }

    public static List<Document> getUserMoments(String username) {
        List<Document> moments = new ArrayList<>();
        try {
            if (username == null || username.isBlank()) {
                return moments;
            }
            getMomentsCollection()
                    .find(Filters.eq("username", username))
                    .sort(Sorts.descending("timestamp"))
                    .into(moments);
        } catch (Exception e) {
            System.err.println("âŒ Failed to load user moments: " + e.getMessage());
        }
        return moments;
    }

    public static Document getMomentById(String momentId) {
        try {
            return getMomentsCollection().find(Filters.eq("_id", new ObjectId(momentId))).first();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean updateMomentCaption(String momentId, String text) {
        try {
            return getMomentsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(momentId)),
                    Updates.set("text", text == null ? "" : text.trim())
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean updateMomentImage(String momentId, String imageUrl) {
        try {
            return getMomentsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(momentId)),
                    Updates.set("imageUrl", imageUrl)
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteMoment(String momentId) {
        try {
            return getMomentsCollection()
                    .deleteOne(Filters.eq("_id", new ObjectId(momentId)))
                    .getDeletedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean toggleLike(String momentId, String username) {
        try {
            MongoCollection<Document> moments = getMomentsCollection();
            Document doc = moments.find(Filters.eq("_id", new ObjectId(momentId))).first();
            if (doc == null) {
                return false;
            }

            List<String> likes = doc.getList("likes", String.class);
            boolean alreadyLiked = likes != null && likes.contains(username);
            if (alreadyLiked) {
                moments.updateOne(Filters.eq("_id", doc.getObjectId("_id")), Updates.pull("likes", username));
                return false;
            }

            moments.updateOne(Filters.eq("_id", doc.getObjectId("_id")), Updates.addToSet("likes", username));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void addComment(String momentId, String username, String text) {
        try {
            Document comment = new Document("username", username)
                    .append("text", text == null ? "" : text)
                    .append("timestamp", System.currentTimeMillis());

            getMomentsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(momentId)),
                    Updates.push("comments", comment)
            );
        } catch (Exception e) {
            System.err.println("âŒ Failed to add comment: " + e.getMessage());
        }
    }
}
