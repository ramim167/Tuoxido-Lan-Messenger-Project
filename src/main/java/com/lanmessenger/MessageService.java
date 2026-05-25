package com.lanmessenger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MessageService {
    private static final int MESSAGE_RETENTION_DAYS = 7;
    private static final long MESSAGE_RETENTION_MILLIS = TimeUnit.DAYS.toMillis(MESSAGE_RETENTION_DAYS);

    private static MongoCollection<Document> getMessagesCollection() {
        MongoDatabase database = MongoDatabaseService.getDatabase();
        return database.getCollection("messages");
    }

    private static MongoCollection<Document> getMessageRequestAcceptCollection() {
        MongoDatabase database = MongoDatabaseService.getDatabase();
        return database.getCollection("message_request_accepts");
    }

    private static boolean isGroupChatId(String chatId) {
        return chatId != null && chatId.length() == 24;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long readLong(Document doc, String key, long fallback) {
        if (doc == null || key == null) return fallback;
        Object value = doc.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private static long messageTimestamp(Document doc) {
        return readLong(doc, "timestamp", now());
    }

    private static long messageActivity(Document doc) {
        long timestamp = messageTimestamp(doc);
        return readLong(doc, "lastModifiedAt", timestamp);
    }

    private static Date messageExpiryDate(long timestamp) {
        return new Date(timestamp + MESSAGE_RETENTION_MILLIS);
    }

    private static String canonicalUserA(String user1, String user2) {
        return user1.compareTo(user2) <= 0 ? user1 : user2;
    }

    private static String canonicalUserB(String user1, String user2) {
        return user1.compareTo(user2) <= 0 ? user2 : user1;
    }

    private static void applyMessageLifecycle(Document message, long timestamp) {
        message.append("timestamp", timestamp)
                .append("lastModifiedAt", timestamp)
                .append("expiresAt", messageExpiryDate(timestamp));
    }

    private static Bson buildRetentionFilter() {
        return Filters.gte("timestamp", now() - MESSAGE_RETENTION_MILLIS);
    }

    private static Bson buildChatFilter(String user1, String user2) {
        Bson chatScope;
        if (isGroupChatId(user2)) {
            chatScope = Filters.eq("receiver", user2);
        } else {
            chatScope = Filters.or(
                    Filters.and(Filters.eq("sender", user1), Filters.eq("receiver", user2)),
                    Filters.and(Filters.eq("sender", user2), Filters.eq("receiver", user1))
            );
        }
        return Filters.and(chatScope, buildRetentionFilter());
    }

    private static int compareByTimestampAndId(Document left, Document right) {
        int byTimestamp = Long.compare(messageTimestamp(left), messageTimestamp(right));
        if (byTimestamp != 0) {
            return byTimestamp;
        }

        ObjectId leftId = left.getObjectId("_id");
        ObjectId rightId = right.getObjectId("_id");
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return -1;
        }
        if (rightId == null) {
            return 1;
        }
        return leftId.compareTo(rightId);
    }

    private static void sortAscending(List<Document> history) {
        history.sort(MessageService::compareByTimestampAndId);
    }

    private static void backfillExpiryMetadata(MongoCollection<Document> collection) {
        try {
            List<Document> pending = new ArrayList<>();
            collection.find()
                    .projection(new Document("_id", 1).append("timestamp", 1).append("lastModifiedAt", 1))
                    .into(pending);

            for (Document doc : pending) {
                ObjectId id = doc.getObjectId("_id");
                if (id == null) {
                    continue;
                }
                long timestamp = messageTimestamp(doc);
                long lastModifiedAt = messageActivity(doc);
                collection.updateOne(
                        Filters.eq("_id", id),
                        Updates.combine(
                                Updates.set("expiresAt", messageExpiryDate(timestamp)),
                                Updates.set("lastModifiedAt", lastModifiedAt)
                        )
                );
            }
        } catch (Exception ignored) {
        }
    }

    private static void attachReadTracking(Document message, String sender, String receiver) {
        if (isGroupChatId(receiver)) {
            message.append("readBy", new ArrayList<>(List.of(sender)));
        }
    }

    private static void syncArchiveState(String sender, String receiver) {
        UserService.toggleArchive(sender, receiver, false);
        if (!isGroupChatId(receiver)) {
            UserService.toggleArchive(receiver, sender, false);
        }
    }

    private static boolean isGroupMessageUnread(Document message, String username) {
        List<String> readBy = message.getList("readBy", String.class);
        return readBy == null || !readBy.contains(username);
    }

    private static boolean areUsersFriends(String user1, String user2) {
        if (user1 == null || user2 == null || user1.isBlank() || user2.isBlank()) {
            return false;
        }
        if (user1.equals(user2)) {
            return true;
        }

        try {
            Document relation = UserService.getRelationshipRecord(user1, user2);
            return relation != null && "accepted".equalsIgnoreCase(relation.getString("status"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasRecentOutgoingMessage(String sender, String receiver) {
        if (sender == null || receiver == null || sender.isBlank() || receiver.isBlank()) {
            return false;
        }

        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson filter = Filters.and(
                    Filters.eq("sender", sender),
                    Filters.eq("receiver", receiver),
                    buildRetentionFilter()
            );
            return messages.find(filter).projection(new Document("_id", 1)).limit(1).first() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean hasRecentAcceptedPair(String user1, String user2) {
        if (user1 == null || user2 == null || user1.isBlank() || user2.isBlank()) {
            return false;
        }
        try {
            String userA = canonicalUserA(user1, user2);
            String userB = canonicalUserB(user1, user2);
            Bson filter = Filters.and(
                    Filters.eq("userA", userA),
                    Filters.eq("userB", userB),
                    buildRetentionFilter()
            );
            return getMessageRequestAcceptCollection()
                    .find(filter)
                    .projection(new Document("_id", 1))
                    .limit(1)
                    .first() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void markAcceptedPair(String user1, String user2) {
        if (user1 == null || user2 == null || user1.isBlank() || user2.isBlank()) {
            return;
        }
        if (isGroupChatId(user1) || isGroupChatId(user2)) {
            return;
        }

        try {
            String userA = canonicalUserA(user1, user2);
            String userB = canonicalUserB(user1, user2);
            long timestamp = now();

            getMessageRequestAcceptCollection().updateOne(
                    Filters.and(Filters.eq("userA", userA), Filters.eq("userB", userB)),
                    Updates.combine(
                            Updates.set("userA", userA),
                            Updates.set("userB", userB),
                            Updates.set("timestamp", timestamp),
                            Updates.set("expiresAt", messageExpiryDate(timestamp))
                    ),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception ignored) {
        }
    }

    public static void clearMessageRequestAcceptance(String user1, String user2) {
        if (user1 == null || user2 == null || user1.isBlank() || user2.isBlank()) {
            return;
        }

        try {
            String userA = canonicalUserA(user1, user2);
            String userB = canonicalUserB(user1, user2);
            getMessageRequestAcceptCollection().deleteMany(
                    Filters.and(Filters.eq("userA", userA), Filters.eq("userB", userB))
            );
        } catch (Exception ignored) {
        }
    }

    private static void syncDirectMessageRequestState(String sender, String receiver) {
        if (sender == null || receiver == null || sender.isBlank() || receiver.isBlank() || sender.equals(receiver)) {
            return;
        }
        if (isGroupChatId(receiver) || isGroupChatId(sender)) {
            return;
        }

        if (areUsersFriends(sender, receiver)) {
            markAcceptedPair(sender, receiver);
            return;
        }

        if (hasRecentAcceptedPair(sender, receiver) || hasRecentOutgoingMessage(receiver, sender)) {
            markAcceptedPair(sender, receiver);
        }
    }

    public static boolean shouldShowInMessageRequests(String myUsername, String partnerUsername) {
        if (myUsername == null || partnerUsername == null
                || myUsername.isBlank() || partnerUsername.isBlank()
                || myUsername.equals(partnerUsername)
                || isGroupChatId(partnerUsername)) {
            return false;
        }

        if (UserService.isBlocked(myUsername, partnerUsername)) {
            return false;
        }

        if (areUsersFriends(myUsername, partnerUsername)) {
            return false;
        }

        if (hasRecentAcceptedPair(myUsername, partnerUsername)) {
            return false;
        }

        boolean hasIncoming = hasRecentOutgoingMessage(partnerUsername, myUsername);
        if (!hasIncoming) {
            return false;
        }

        boolean hasOutgoing = hasRecentOutgoingMessage(myUsername, partnerUsername);
        return !hasOutgoing;
    }

    public static boolean acceptMessageRequest(String myUsername, String partnerUsername) {
        if (myUsername == null || partnerUsername == null || myUsername.isBlank() || partnerUsername.isBlank()) {
            return false;
        }
        if (UserService.isBlocked(myUsername, partnerUsername)) {
            return false;
        }
        markAcceptedPair(myUsername, partnerUsername);
        return true;
    }

    public static boolean deleteMessageRequest(String myUsername, String partnerUsername) {
        if (myUsername == null || partnerUsername == null || myUsername.isBlank() || partnerUsername.isBlank()) {
            return false;
        }
        try {
            clearMessageRequestAcceptance(myUsername, partnerUsername);
            deleteChatHistory(myUsername, partnerUsername);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Document sendMessage(String sender, String receiver, String text, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();

            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", text)
                    .append("isEdited", false)
                    .append("isRead", false);

            applyMessageLifecycle(message, timestamp);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);

            // Main message insert first
            messages.insertOne(message);

            // Extra sync work in background, so sending feels faster
            Thread syncThread = new Thread(() -> {
                syncDirectMessageRequestState(sender, receiver);
                syncArchiveState(sender, receiver);
            }, "message-post-sync");
            syncThread.setDaemon(true);
            syncThread.start();

            return message;

        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public static void sendSystemMessage(String receiverId, String text, String senderUsername) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();
            Document message = new Document("sender", senderUsername)
                    .append("receiver", receiverId)
                    .append("text", text)
                    .append("isSystemMsg", true)
                    .append("isEdited", false)
                    .append("isRead", false);
            applyMessageLifecycle(message, timestamp);

            attachReadTracking(message, senderUsername, receiverId);
            messages.insertOne(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendGroupMemberAddedSystemMessage(String groupId, String actorUsername, String addedUsername) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();
            Document message = new Document("sender", actorUsername)
                    .append("receiver", groupId)
                    .append("text", "added " + addedUsername + " the group")
                    .append("isSystemMsg", true)
                    .append("systemType", "group_member_added")
                    .append("systemTargetUsername", addedUsername)
                    .append("isEdited", false)
                    .append("isRead", false);
            applyMessageLifecycle(message, timestamp);

            attachReadTracking(message, actorUsername, groupId);
            messages.insertOne(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendImageMessage(String sender, String receiver, String imageUrl, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();
            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", imageUrl)
                    .append("isImage", true)
                    .append("isEdited", false);
            applyMessageLifecycle(message, timestamp);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);
            messages.insertOne(message);
            syncDirectMessageRequestState(sender, receiver);
            syncArchiveState(sender, receiver);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendFileMessage(String sender, String receiver, String base64Data, String fileName, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();
            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", base64Data)
                    .append("fileName", fileName)
                    .append("isFile", true)
                    .append("isEdited", false);
            applyMessageLifecycle(message, timestamp);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);
            messages.insertOne(message);
            syncDirectMessageRequestState(sender, receiver);
            syncArchiveState(sender, receiver);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void markMessagesAsRead(String myUsername, String partnerUsername) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            if (isGroupChatId(partnerUsername)) {
                messages.updateMany(
                        Filters.and(
                                Filters.eq("receiver", partnerUsername),
                                Filters.ne("sender", myUsername),
                                Filters.ne("readBy", myUsername)
                        ),
                        Updates.addToSet("readBy", myUsername)
                );
                return;
            }

            messages.updateMany(
                    Filters.and(
                            Filters.eq("sender", partnerUsername),
                            Filters.eq("receiver", myUsername),
                            Filters.or(
                                    Filters.eq("isRead", false),
                                    Filters.exists("isRead", false)
                            )
                    ),
                    Updates.set("isRead", true)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean unsendMessage(String messageId) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            messages.updateOne(
                    Filters.eq("_id", new org.bson.types.ObjectId(messageId)),
                    Updates.combine(
                            Updates.set("isUnsent", true),
                            Updates.set("text", ""),
                            Updates.set("isEdited", false),
                            Updates.set("lastModifiedAt", now()),
                            Updates.unset("isImage"),
                            Updates.unset("isFile"),
                            Updates.unset("fileName"),
                            Updates.unset("replyName"),
                            Updates.unset("replyText")
                    )
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean editMessage(String messageId, String newText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            messages.updateOne(
                    Filters.eq("_id", new org.bson.types.ObjectId(messageId)),
                    Updates.combine(
                            Updates.set("text", newText),
                            Updates.set("isEdited", true),
                            Updates.set("lastModifiedAt", now())
                    )
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Document> getChatHistory(String user1, String user2) {
        List<Document> history = new ArrayList<>();
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson filter = buildChatFilter(user1, user2);
            messages.find(filter)
                    .sort(Sorts.orderBy(Sorts.ascending("timestamp"), Sorts.ascending("_id")))
                    .into(history);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    public static List<Document> getLatestChatHistory(String user1, String user2, int limit) {
        List<Document> history = new ArrayList<>();
        if (limit <= 0) return history;
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson filter = buildChatFilter(user1, user2);
            messages.find(filter)
                    .sort(Sorts.orderBy(Sorts.descending("timestamp"), Sorts.descending("_id")))
                    .limit(limit)
                    .into(history);
            sortAscending(history);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    public static List<Document> getChatHistoryBefore(String user1, String user2, long beforeTimestamp, String beforeMessageId, int limit) {
        List<Document> history = new ArrayList<>();
        if (limit <= 0) return history;
        if (beforeTimestamp <= 0) {
            return getLatestChatHistory(user1, user2, limit);
        }

        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson chatFilter = buildChatFilter(user1, user2);
            Bson beforeFilter = Filters.lt("timestamp", beforeTimestamp);

            if (beforeMessageId != null && !beforeMessageId.isBlank()) {
                try {
                    ObjectId beforeId = new ObjectId(beforeMessageId);
                    beforeFilter = Filters.or(
                            Filters.lt("timestamp", beforeTimestamp),
                            Filters.and(
                                    Filters.eq("timestamp", beforeTimestamp),
                                    Filters.lt("_id", beforeId)
                            )
                    );
                } catch (Exception ignored) {
                }
            }

            messages.find(Filters.and(chatFilter, beforeFilter))
                    .sort(Sorts.orderBy(Sorts.descending("timestamp"), Sorts.descending("_id")))
                    .limit(limit)
                    .into(history);
            sortAscending(history);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    public static List<Document> getChatChangesSince(String user1, String user2, long sinceTimestamp, int limit) {
        List<Document> changes = new ArrayList<>();
        if (limit <= 0) return changes;
        long threshold = Math.max(0L, sinceTimestamp);
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson chatFilter = buildChatFilter(user1, user2);
            Bson changeFilter = Filters.or(
                    Filters.gte("timestamp", threshold),
                    Filters.gte("lastModifiedAt", threshold)
            );

            messages.find(Filters.and(chatFilter, changeFilter))
                    .sort(Sorts.orderBy(Sorts.ascending("timestamp"), Sorts.ascending("_id")))
                    .limit(limit)
                    .into(changes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return changes;
    }

    public static void setupAutoDelete() {
        try {
            MongoCollection<Document> collection = getMessagesCollection();
            MongoCollection<Document> acceptCollection = getMessageRequestAcceptCollection();
            try {
                collection.dropIndex("timestamp_1");
            } catch (Exception ignored) {
            }

            IndexOptions indexOptions = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS);
            collection.createIndex(new Document("expiresAt", 1), indexOptions);
            collection.createIndex(new Document("timestamp", 1));
            collection.createIndex(new Document("lastModifiedAt", 1));

            acceptCollection.createIndex(new Document("expiresAt", 1), indexOptions);
            acceptCollection.createIndex(new Document("userA", 1).append("userB", 1));
            acceptCollection.createIndex(new Document("timestamp", 1));

            backfillExpiryMetadata(collection);
            System.out.println("Auto-delete activated: Messages will vanish after 7 days.");
        } catch (Exception ignored) {
        }
    }

    public static List<String> getRecentChatPartners(String myUsername) {
        Map<String, Document> partnerLastMessage = new LinkedHashMap<>();
        Map<String, Integer> unreadCounts = new HashMap<>();
        List<String> formattedList = new ArrayList<>();

        try {
            MongoCollection<Document> messages = getMessagesCollection();
            MongoCollection<Document> users = MongoDatabaseService.getDatabase().getCollection("users");
            MongoCollection<Document> groups = GroupService.getGroupsCollection();

            List<Document> myGroups = new ArrayList<>();
            groups.find(Filters.in("members", myUsername)).into(myGroups);

            Set<String> myGroupIds = new HashSet<>();
            Map<String, String> groupNames = new HashMap<>();
            for (Document group : myGroups) {
                String groupId = group.getObjectId("_id").toString();
                myGroupIds.add(groupId);
                groupNames.put(groupId, group.getString("groupName"));
            }

            Bson personalFilter = Filters.or(
                    Filters.eq("sender", myUsername),
                    Filters.eq("receiver", myUsername)
            );

            Bson conversationScope = myGroupIds.isEmpty()
                    ? personalFilter
                    : Filters.or(personalFilter, Filters.in("receiver", myGroupIds));
            Bson finalFilter = Filters.and(conversationScope, buildRetentionFilter());

            messages.find(finalFilter).sort(Sorts.descending("timestamp")).forEach(message -> {
                String sender = message.getString("sender");
                String receiver = message.getString("receiver");
                String partner = myGroupIds.contains(receiver) ? receiver : (myUsername.equals(sender) ? receiver : sender);

                if (partner == null || partner.isBlank()) {
                    return;
                }

                partnerLastMessage.putIfAbsent(partner, message);

                if (myGroupIds.contains(partner)) {
                    if (!myUsername.equals(sender) && isGroupMessageUnread(message, myUsername)) {
                        unreadCounts.merge(partner, 1, Integer::sum);
                    }
                } else if (myUsername.equals(receiver) && partner.equals(sender) && !Boolean.TRUE.equals(message.getBoolean("isRead"))) {
                    unreadCounts.merge(partner, 1, Integer::sum);
                }
            });

            Map<String, Document> userDocs = new HashMap<>();
            List<String> directPartners = new ArrayList<>();
            for (String partner : partnerLastMessage.keySet()) {
                if (!myGroupIds.contains(partner)) {
                    directPartners.add(partner);
                }
            }
            if (!directPartners.isEmpty()) {
                List<Document> directUsers = new ArrayList<>();
                users.find(Filters.in("username", directPartners)).into(directUsers);
                for (Document userDoc : directUsers) {
                    userDocs.put(userDoc.getString("username"), userDoc);
                }
            }

            for (Map.Entry<String, Document> entry : partnerLastMessage.entrySet()) {
                String partner = entry.getKey();
                Document message = entry.getValue();

                long timestamp = message.containsKey("timestamp")
                        ? messageTimestamp(message)
                        : now();
                String sender = message.getString("sender");
                String preview = buildPreviewText(message, sender, myUsername, myGroupIds.contains(partner));
                int unreadCount = unreadCounts.getOrDefault(partner, 0);

                if (myGroupIds.contains(partner)) {
                    String groupName = groupNames.get(partner);
                    if (groupName != null) {
                        formattedList.add(groupName + " (@" + partner + "):::" + timestamp + ":::" + preview + ":::" + unreadCount);
                    }
                } else {
                    Document userDoc = userDocs.get(partner);
                    if (userDoc != null) {
                        formattedList.add(userDoc.getString("name") + " (@" + partner + "):::" + timestamp + ":::" + preview + ":::" + unreadCount);
                    }
                }
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            boolean interruptedLockWait = errorMessage != null && errorMessage.contains("Interrupted waiting for lock");
            if (!interruptedLockWait && !Thread.currentThread().isInterrupted()) {
                System.err.println("Failed to load recent chats: " + errorMessage);
            }
        }

        return formattedList;
    }

    private static String buildPreviewText(Document message, String sender, String myUsername, boolean groupChat) {
        String text = message.getString("text");
        boolean unsent = Boolean.TRUE.equals(message.getBoolean("isUnsent"));
        boolean systemMessage = Boolean.TRUE.equals(message.getBoolean("isSystemMsg"));
        boolean image = Boolean.TRUE.equals(message.getBoolean("isImage"));
        boolean file = Boolean.TRUE.equals(message.getBoolean("isFile"));

        if (unsent) {
            return myUsername.equals(sender) ? "You unsent a message" : sender + " unsent a message";
        }

        if (systemMessage) {
            return formatSystemMessage(message, myUsername);
        }

        if (image) {
            return groupChat && !myUsername.equals(sender) ? sender + ": Photo" : "Photo";
        }

        if (file) {
            return groupChat && !myUsername.equals(sender) ? sender + ": File" : "File";
        }

        if (text == null || text.isBlank()) {
            return "Attachment";
        }

        String preview = text.replace("\n", " ").replace("\r", " ");
        if (preview.length() > 22) {
            preview = preview.substring(0, 22) + "...";
        }

        if (groupChat && sender != null && !myUsername.equals(sender)) {
            return sender + ": " + preview;
        }
        return preview;
    }

    public static String formatSystemMessage(Document message, String viewerUsername) {
        if (message == null) {
            return "";
        }

        String sender = message.getString("sender");
        String text = message.getString("text");
        String systemType = message.getString("systemType");

        if ("group_member_added".equals(systemType)) {
            String targetUsername = message.getString("systemTargetUsername");
            if (viewerUsername != null && viewerUsername.equals(sender)) {
                return "You added " + targetUsername + " the group";
            }
            if (viewerUsername != null && viewerUsername.equals(targetUsername)) {
                return sender + " added you the group";
            }
            return sender + " added " + targetUsername + " the group";
        }

        return viewerUsername != null && viewerUsername.equals(sender)
                ? "You " + text
                : sender + " " + text;
    }

    public static void deleteChatHistory(String user1, String user2) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Bson filter = Filters.or(
                    Filters.and(Filters.eq("sender", user1), Filters.eq("receiver", user2)),
                    Filters.and(Filters.eq("sender", user2), Filters.eq("receiver", user1))
            );
            messages.deleteMany(filter);

            UserService.toggleArchive(user1, user2, false);
            UserService.toggleArchive(user2, user1, false);
            clearMessageRequestAcceptance(user1, user2);
        } catch (Exception e) {
            System.err.println("Failed to delete chat: " + e.getMessage());
        }
    }

    private static MongoCollection<Document> getCallsCollection() {
        return MongoDatabaseService.getDatabase().getCollection("calls");
    }

    public static boolean initiateCall(String callerUsername, String receiverUsername, String callType) {
        try {
            MongoCollection<Document> calls = getCallsCollection();

            calls.deleteMany(Filters.eq("caller", callerUsername));

            Bson filter = Filters.or(
                    Filters.eq("caller", receiverUsername),
                    Filters.eq("receiver", receiverUsername)
            );

            if (calls.find(filter).first() != null) {
                return false;
            }

            String myIp = java.net.InetAddress.getLocalHost().getHostAddress();

            Document newCall = new Document("caller", callerUsername)
                    .append("receiver", receiverUsername)
                    .append("type", callType)
                    .append("status", "ringing")
                    .append("ip", myIp)
                    .append("timestamp", System.currentTimeMillis());

            calls.insertOne(newCall);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Document checkIncomingCall(String myUsername) {
        try {
            return getCallsCollection().find(
                    Filters.and(
                            Filters.eq("receiver", myUsername),
                            Filters.or(
                                    Filters.eq("status", "ringing"),
                                    Filters.eq("status", "accepted")
                            )
                    )
            ).first();
        } catch (Exception e) {
            return null;
        }
    }

    public static void updateCallStatus(String callerUsername, String receiverUsername, String status) {
        try {
            Bson filter = Filters.and(
                    Filters.eq("caller", callerUsername),
                    Filters.eq("receiver", receiverUsername)
            );
            if ("ended".equals(status) || "rejected".equals(status)) {
                getCallsCollection().deleteOne(filter);
            } else {
                getCallsCollection().updateOne(filter, Updates.set("status", status));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void logCall(String callerUsername, String receiverUsername, String callType, long durationMs, String endedBy) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            long timestamp = now();

            String cType = (callType != null && !callType.isEmpty())
                    ? callType.substring(0, 1).toUpperCase() + callType.substring(1).toLowerCase()
                    : "Unknown";

            String finalMessageText;
            if (durationMs == 0 || "missed".equals(endedBy)) {
                finalMessageText = "Missed " + cType + " Call";
            } else {
                long secs = durationMs / 1000;
                long mins = secs / 60;
                secs = secs % 60;
                String timeStr = String.format("%02d:%02d", mins, secs);
                finalMessageText = cType + " Call Ended - " + timeStr;
            }

            Document log = new Document("sender", callerUsername)
                    .append("receiver", receiverUsername)
                    .append("text", finalMessageText)
                    .append("isRead", false)
                    .append("isCallLog", true)
                    .append("callType", callType)
                    .append("callDuration", durationMs)
                    .append("endedBy", endedBy);
            applyMessageLifecycle(log, timestamp);

            messages.insertOne(log);
            syncArchiveState(callerUsername, receiverUsername);
        } catch (Exception e) {
            System.err.println("Failed to log call: " + e.getMessage());
        }
    }
}
