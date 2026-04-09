package com.lanmessenger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MessageService {

    private static MongoCollection<Document> getMessagesCollection() {
        MongoDatabase database = MongoDatabaseService.getDatabase();
        return database.getCollection("messages");
    }

    private static boolean isGroupChatId(String chatId) {
        return chatId != null && chatId.length() == 24;
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

    public static void sendMessage(String sender, String receiver, String text, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", text)
                    .append("timestamp", System.currentTimeMillis())
                    .append("isEdited", false)
                    .append("isRead", false);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);
            messages.insertOne(message);
            syncArchiveState(sender, receiver);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendSystemMessage(String receiverId, String text, String senderUsername) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Document message = new Document("sender", senderUsername)
                    .append("receiver", receiverId)
                    .append("text", text)
                    .append("isSystemMsg", true)
                    .append("timestamp", System.currentTimeMillis())
                    .append("isEdited", false)
                    .append("isRead", false);

            attachReadTracking(message, senderUsername, receiverId);
            messages.insertOne(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendGroupMemberAddedSystemMessage(String groupId, String actorUsername, String addedUsername) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Document message = new Document("sender", actorUsername)
                    .append("receiver", groupId)
                    .append("text", "added " + addedUsername + " the group")
                    .append("isSystemMsg", true)
                    .append("systemType", "group_member_added")
                    .append("systemTargetUsername", addedUsername)
                    .append("timestamp", System.currentTimeMillis())
                    .append("isEdited", false)
                    .append("isRead", false);

            attachReadTracking(message, actorUsername, groupId);
            messages.insertOne(message);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendImageMessage(String sender, String receiver, String imageUrl, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", imageUrl)
                    .append("isImage", true)
                    .append("timestamp", System.currentTimeMillis())
                    .append("isEdited", false);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);
            messages.insertOne(message);
            syncArchiveState(sender, receiver);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static void sendFileMessage(String sender, String receiver, String base64Data, String fileName, String replyName, String replyText) {
        try {
            MongoCollection<Document> messages = getMessagesCollection();
            Document message = new Document("sender", sender)
                    .append("receiver", receiver)
                    .append("text", base64Data)
                    .append("fileName", fileName)
                    .append("isFile", true)
                    .append("timestamp", System.currentTimeMillis())
                    .append("isEdited", false);

            if (replyName != null && replyText != null) {
                message.append("replyName", replyName).append("replyText", replyText);
            }

            attachReadTracking(message, sender, receiver);
            messages.insertOne(message);
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
                            Updates.set("isEdited", true)
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
            Bson filter;

            if (isGroupChatId(user2)) {
                filter = Filters.eq("receiver", user2);
            } else {
                filter = Filters.or(
                        Filters.and(Filters.eq("sender", user1), Filters.eq("receiver", user2)),
                        Filters.and(Filters.eq("sender", user2), Filters.eq("receiver", user1))
                );
            }

            messages.find(filter).sort(Sorts.ascending("timestamp")).into(history);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    public static void setupAutoDelete() {
        try {
            MongoCollection<Document> collection = getMessagesCollection();
            IndexOptions indexOptions = new IndexOptions().expireAfter(48L, TimeUnit.HOURS);
            collection.createIndex(new Document("timestamp", 1), indexOptions);
            System.out.println("Auto-delete activated: Messages will vanish after 48 hours.");
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

            Bson finalFilter = myGroupIds.isEmpty()
                    ? personalFilter
                    : Filters.or(personalFilter, Filters.in("receiver", myGroupIds));

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
                        ? message.getLong("timestamp")
                        : System.currentTimeMillis();
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
                    .append("timestamp", System.currentTimeMillis())
                    .append("isRead", false)
                    .append("isCallLog", true)
                    .append("callType", callType)
                    .append("callDuration", durationMs)
                    .append("endedBy", endedBy);

            messages.insertOne(log);
            syncArchiveState(callerUsername, receiverUsername);
        } catch (Exception e) {
            System.err.println("Failed to log call: " + e.getMessage());
        }
    }
}
