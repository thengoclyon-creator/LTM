package com.lan.chat.model;

import java.util.Objects;

public class Peer {
    private String id;
    private final String ip;
    private int port;
    private String nickname;
    private boolean isOnline;
    private boolean isConnected;
    private long lastSeen;
    private int voiceUdpPort = 6000;
    private boolean autoDiscovered = false;

    public Peer(String ip, int port, String nickname) {
        this(null, ip, port, nickname);
    }

    public Peer(String id, String ip, int port, String nickname) {
        this.id = id;
        this.ip = ip;
        this.port = port;
        this.nickname = (nickname == null || nickname.trim().isEmpty()) ? (ip + ":" + port) : nickname;
        this.isOnline = true;
        this.isConnected = false;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getVoiceUdpPort() { return voiceUdpPort; }
    public void setVoiceUdpPort(int voiceUdpPort) { this.voiceUdpPort = voiceUdpPort; }
    public boolean isAutoDiscovered() { return autoDiscovered; }
    public void setAutoDiscovered(boolean autoDiscovered) { this.autoDiscovered = autoDiscovered; }

    public String getIp() { return ip; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
    public boolean isConnected() { return isConnected; }
    public void setConnected(boolean connected) { isConnected = connected; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public String getAddressKey() {
        return (id != null && !id.trim().isEmpty()) ? id : (ip + ":" + port);
    }

    public boolean isSamePeer(Peer other) {
        if (other == null) return false;
        if (this == other) return true;
        if (this.id != null && other.id != null && !this.id.isEmpty() && !other.id.isEmpty()) {
            return this.id.equals(other.id);
        }
        return this.port == other.port && Objects.equals(this.ip, other.ip);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Peer peer = (Peer) o;
        if (this.id != null && peer.id != null && !this.id.isEmpty() && !peer.id.isEmpty()) {
            return this.id.equals(peer.id);
        }
        return port == peer.port && Objects.equals(ip, peer.ip);
    }

    @Override
    public int hashCode() {
        if (id != null && !id.isEmpty()) {
            return Objects.hash(id);
        }
        return Objects.hash(ip, port);
    }

    @Override
    public String toString() {
        return nickname + " (" + ip + ":" + port + ")";
    }
}
