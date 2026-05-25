package com.lanmessenger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MomentsService {
    private static final int MOMENT_LIFETIME_DAYS = 7;
    private static final long MOMENT_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(MOMENT_LIFETIME_DAYS);
    private static final int REPLY_PREVIEW_MAX = 140;

    private static MongoCollection<Document> getMomentsCollection() {
        MongoDatabase database = MongoDatabaseService.getDatabase();
        return database.getCollection("moments");
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long activeSince() {
        return now() - MOMENT_LIFETIME_MILLIS;
    }

    private static long momentTimestamp(Document doc) {
        if (doc == null) return now();
        Object value = doc.get("timestamp");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return now();
    }

    private static Date expiryFor(long timestamp) {
        return new Date(timestamp + MOMENT_LIFETIME_MILLIS);
    }

    private static Bson activeMomentFilter() {
        return Filters.gte("timestamp", activeSince());
    }

    private static Bson activeMomentById(ObjectId id) {
        return Filters.and(Filters.eq("_id", id), activeMomentFilter());
    }

    private static void backfillExpiryMetadata(MongoCollection<Document> moments) {
        try {
            List<Document> pending = new ArrayList<>();
            moments.find(Filters.exists("expiresAt", false))
                    .projection(new Document("_id", 1).append("timestamp", 1))
                    .into(pending);

            for (Document doc : pending) {
                ObjectId id = doc.getObjectId("_id");
                if (id == null) continue;
                long ts = momentTimestamp(doc);
                moments.updateOne(
                        Filters.eq("_id", id),
                        Updates.set("expiresAt", expiryFor(ts))
                );
            }
        } catch (Exception ignored) {
        }
    }

    public static void setupAutoDelete() {
        try {
            MongoCollection<Document> moments = getMomentsCollection();
            IndexOptions ttl = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS);
            moments.createIndex(new Document("expiresAt", 1), ttl);
            moments.createIndex(new Document("timestamp", 1));
            backfillExpiryMetadata(moments);
            System.out.println("Moments auto-delete activated: moments expire after 7 days.");
        } catch (Exception ignored) {
        }
    }

    public static void createMoment(String username, String text, String imageUrl) {
        try {
            long timestamp = now();
            Document doc = new Document("username", username)
                    .append("text", text == null ? "" : text)
                    .append("imageUrl", imageUrl)
                    .append("timestamp", timestamp)
                    .append("expiresAt", expiryFor(timestamp))
                    .append("likes", new ArrayList<String>())
                    .append("comments", new ArrayList<Document>());

            getMomentsCollection().insertOne(doc);
        } catch (Exception e) {
            System.err.println("Failed to create moment: " + e.getMessage());
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
                    .find(Filters.and(Filters.in("username", users), activeMomentFilter()))
                    .sort(Sorts.descending("timestamp"))
                    .into(feed);
        } catch (Exception e) {
            System.err.println("Failed to load moments feed: " + e.getMessage());
        }
        return feed;
    }

    public static List<Document> getFeedPage(String myUsername, List<String> friendUsernames, int limit, Long beforeTimestamp, String beforeMomentId) {
        List<Document> feed = new ArrayList<>();
        if (limit <= 0) {
            return feed;
        }

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

            Bson filter = Filters.and(Filters.in("username", users), activeMomentFilter());
            if (beforeTimestamp != null && beforeTimestamp > 0) {
                Bson cursorFilter = Filters.lt("timestamp", beforeTimestamp);
                if (beforeMomentId != null && !beforeMomentId.isBlank()) {
                    try {
                        ObjectId beforeId = new ObjectId(beforeMomentId);
                        cursorFilter = Filters.or(
                                Filters.lt("timestamp", beforeTimestamp),
                                Filters.and(
                                        Filters.eq("timestamp", beforeTimestamp),
                                        Filters.lt("_id", beforeId)
                                )
                        );
                    } catch (Exception ignored) {
                    }
                }
                filter = Filters.and(filter, cursorFilter);
            }

            getMomentsCollection()
                    .find(filter)
                    .sort(Sorts.orderBy(Sorts.descending("timestamp"), Sorts.descending("_id")))
                    .limit(limit)
                    .into(feed);
        } catch (Exception e) {
            System.err.println("Failed to load moments feed page: " + e.getMessage());
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
                    .find(Filters.and(Filters.eq("username", username), activeMomentFilter()))
                    .sort(Sorts.descending("timestamp"))
                    .into(moments);
        } catch (Exception e) {
            System.err.println("Failed to load user moments: " + e.getMessage());
        }
        return moments;
    }

    public static Document getMomentById(String momentId) {
        try {
            return getMomentsCollection().find(activeMomentById(new ObjectId(momentId))).first();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean updateMomentCaption(String momentId, String text) {
        try {
            return getMomentsCollection().updateOne(
                    activeMomentById(new ObjectId(momentId)),
                    Updates.set("text", text == null ? "" : text.trim())
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean updateMomentImage(String momentId, String imageUrl) {
        try {
            return getMomentsCollection().updateOne(
                    activeMomentById(new ObjectId(momentId)),
                    Updates.set("imageUrl", imageUrl)
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteMoment(String momentId) {
        try {
            return getMomentsCollection()
                    .deleteOne(activeMomentById(new ObjectId(momentId)))
                    .getDeletedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean toggleLike(String momentId, String username) {
        try {
            MongoCollection<Document> moments = getMomentsCollection();
            ObjectId id = new ObjectId(momentId);
            Document doc = moments.find(activeMomentById(id)).first();
            if (doc == null) {
                return false;
            }

            List<String> likes = doc.getList("likes", String.class);
            boolean alreadyLiked = likes != null && likes.contains(username);
            if (alreadyLiked) {
                moments.updateOne(activeMomentById(id), Updates.pull("likes", username));
                return false;
            }

            moments.updateOne(activeMomentById(id), Updates.addToSet("likes", username));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void addComment(String momentId, String username, String text) {
        addComment(momentId, username, text, null, null, null);
    }

    public static void addComment(
            String momentId,
            String username,
            String text,
            String parentCommentId,
            String replyToUsername,
            String replyPreview
    ) {
        try {
            String normalizedText = text == null ? "" : text.trim();
            if (normalizedText.isEmpty()) {
                return;
            }

            Document comment = new Document("commentId", new ObjectId().toHexString())
                    .append("username", username)
                    .append("text", normalizedText)
                    .append("timestamp", System.currentTimeMillis());

            String normalizedParentId = normalizeRef(parentCommentId);
            if (normalizedParentId != null) {
                comment.append("parentCommentId", normalizedParentId);
            }

            String normalizedReplyUser = normalizeRef(replyToUsername);
            if (normalizedReplyUser != null) {
                comment.append("replyToUsername", normalizedReplyUser);
            }

            String normalizedReplyPreview = normalizeRef(replyPreview);
            if (normalizedReplyPreview != null) {
                comment.append("replyPreview", truncate(normalizedReplyPreview, REPLY_PREVIEW_MAX));
            }

            getMomentsCollection().updateOne(
                    activeMomentById(new ObjectId(momentId)),
                    Updates.push("comments", comment)
            );
        } catch (Exception e) {
            System.err.println("Failed to add comment: " + e.getMessage());
        }
    }

    private static String normalizeRef(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 1) {
            return value.substring(0, 1);
        }
        return value.substring(0, maxChars - 1) + "...";
    }
}
