package com.lanmessenger;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GroupService {

    public static MongoCollection<Document> getGroupsCollection() {
        return MongoDatabaseService.getDatabase().getCollection("groups");
    }

    public static String createGroup(String groupName, String creatorUsername, List<String> members) {
        try {

            if (!members.contains(creatorUsername)) {
                members.add(creatorUsername);
            }

            Document group = new Document("groupName", groupName)
                    .append("creator", creatorUsername)
                    .append("members", members)
                    .append("groupPic", "")
                    .append("isGroup", true)
                    .append("timestamp", System.currentTimeMillis());

            getGroupsCollection().insertOne(group);

            String groupId = group.getObjectId("_id").toString();

            MessageService.sendSystemMessage(groupId, "created the group", creatorUsername);

            return groupId;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Document getGroupById(String groupId) {
        try {
            return getGroupsCollection().find(Filters.eq("_id", new ObjectId(groupId))).first();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean updateGroupName(String groupId, String groupName) {
        try {
            return getGroupsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(groupId)),
                    Updates.set("groupName", groupName == null ? "" : groupName)
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean updateGroupPhoto(String groupId, String groupPhotoUrl) {
        try {
            return getGroupsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(groupId)),
                    Updates.set("groupPic", groupPhotoUrl == null ? "" : groupPhotoUrl)
            ).getMatchedCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<String> addMembers(String groupId, List<String> candidateMembers) {
        List<String> addedMembers = new ArrayList<>();
        if (candidateMembers == null || candidateMembers.isEmpty()) {
            return addedMembers;
        }

        try {
            Document group = getGroupById(groupId);
            if (group == null) {
                return addedMembers;
            }

            List<String> existingMembers = group.getList("members", String.class);
            Set<String> mergedMembers = new LinkedHashSet<>();
            if (existingMembers != null) {
                mergedMembers.addAll(existingMembers);
            }

            for (String candidate : candidateMembers) {
                if (candidate != null && !candidate.isBlank() && !mergedMembers.contains(candidate)) {
                    mergedMembers.add(candidate);
                    addedMembers.add(candidate);
                }
            }

            if (addedMembers.isEmpty()) {
                return addedMembers;
            }

            getGroupsCollection().updateOne(
                    Filters.eq("_id", new ObjectId(groupId)),
                    Updates.set("members", new ArrayList<>(mergedMembers))
            );
        } catch (Exception e) {
            addedMembers.clear();
        }

        return addedMembers;
    }
}
