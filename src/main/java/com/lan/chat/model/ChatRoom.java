package com.lan.chat.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a custom private group room created by users.
 * Supports member management and room renaming.
 */
public class ChatRoom {
    private final String id;
    private String name;
    private final String creator;
    private final Set<String> memberNicknames = Collections.synchronizedSet(new LinkedHashSet<>());
    private final long createdAt;
    private int unreadCount = 0;

    public ChatRoom(String id, String name, String creator, List<String> members, long createdAt) {
        this.id = id;
        this.name = name != null ? name.trim() : "Nhóm mới";
        this.creator = creator != null ? creator.trim() : "";
        if (members != null) {
            for (String m : members) {
                if (m != null && !m.trim().isEmpty()) {
                    this.memberNicknames.add(m.trim());
                }
            }
        }
        if (this.creator != null && !this.creator.isEmpty()) {
            this.memberNicknames.add(this.creator);
        }
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public ChatRoom(String id, String name, String creator, List<String> members) {
        this(id, name, creator, members, System.currentTimeMillis());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name.trim() : "Nhóm mới"; }
    public String getCreator() { return creator; }
    public List<String> getMembers() { return new ArrayList<>(memberNicknames); }
    public long getCreatedAt() { return createdAt; }

    public void addMember(String nickname) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            memberNicknames.add(nickname.trim());
        }
    }

    public void removeMember(String nickname) {
        if (nickname != null) {
            memberNicknames.remove(nickname.trim());
        }
    }

    public void setMembers(List<String> newMembers) {
        synchronized (memberNicknames) {
            memberNicknames.clear();
            if (newMembers != null) {
                for (String m : newMembers) {
                    if (m != null && !m.trim().isEmpty()) {
                        memberNicknames.add(m.trim());
                    }
                }
            }
            if (creator != null && !creator.isEmpty()) {
                memberNicknames.add(creator);
            }
        }
    }

    public boolean hasMember(String nickname) {
        if (nickname == null) return false;
        synchronized (memberNicknames) {
            for (String m : memberNicknames) {
                if (m.equalsIgnoreCase(nickname.trim())) return true;
            }
        }
        return false;
    }

    public int getUnreadCount() { return unreadCount; }
    public void incrementUnread() { unreadCount++; }
    public void clearUnread() { unreadCount = 0; }

    public String getMembersSummary() {
        synchronized (memberNicknames) {
            return String.join(", ", memberNicknames);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRoom chatRoom)) return false;
        return id != null && id.equals(chatRoom.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
