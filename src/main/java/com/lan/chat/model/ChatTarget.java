package com.lan.chat.model;

/**
 * Unified target representation for the chat interface:
 * - ALL_LAN (Broadcast to all LAN devices)
 * - ROOM (Custom private group room)
 * - PEER (Direct 1-1 conversation)
 */
public class ChatTarget {
    public enum Type { ALL_LAN, ROOM, PEER }

    private final Type type;
    private final ChatRoom room;
    private final Peer peer;

    private ChatTarget(Type type, ChatRoom room, Peer peer) {
        this.type = type;
        this.room = room;
        this.peer = peer;
    }

    public static ChatTarget allLan() {
        return new ChatTarget(Type.ALL_LAN, null, null);
    }

    public static ChatTarget forRoom(ChatRoom room) {
        return new ChatTarget(Type.ROOM, room, null);
    }

    public static ChatTarget forPeer(Peer peer) {
        return new ChatTarget(Type.PEER, null, peer);
    }

    public Type getType() { return type; }
    public ChatRoom getRoom() { return room; }
    public Peer getPeer() { return peer; }

    public boolean isAllLan() { return type == Type.ALL_LAN; }
    public boolean isRoom() { return type == Type.ROOM; }
    public boolean isPeer() { return type == Type.PEER; }

    public String getDisplayName() {
        if (isAllLan()) return "Phòng Chat Chung";
        if (isRoom()) return room.getName();
        return peer != null ? peer.getNickname() : "Ẩn danh";
    }

    public String getKey() {
        if (isAllLan()) return "GROUP";
        if (isRoom()) return "ROOM_" + room.getId();
        if (peer == null) return "PEER_unknown";
        String name = peer.getNickname();
        if (name == null || name.trim().isEmpty() || name.equals(peer.getIp() + ":" + peer.getPort())) {
            return "PEER_" + peer.getAddressKey();
        }
        return "PEER_" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatTarget that)) return false;
        if (type != that.type) return false;
        if (type == Type.ALL_LAN) return true;
        if (type == Type.ROOM) return room != null && room.equals(that.room);
        return peer != null && peer.equals(that.peer);
    }

    @Override
    public int hashCode() {
        if (type == Type.ALL_LAN) return 0;
        if (type == Type.ROOM) return room != null ? room.hashCode() : 0;
        return peer != null ? peer.hashCode() : 0;
    }
}
