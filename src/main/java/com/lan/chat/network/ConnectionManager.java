package com.lan.chat.network;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Peer;
import com.lan.chat.util.AudioNotifier;
import com.lan.chat.util.NetworkUtil;
import com.lan.chat.util.SecurityUtil;
import com.lan.chat.voice.VoiceCallService;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ConnectionManager coordinates:
 * - Multi-Peer Connections (Group chat & mesh network)
 * - TCP Server & manual/auto client connections
 * - UDP Auto-Discovery Service
 * - UDP Voice Call Service
 * - End-to-End Encryption (AES)
 */
public class ConnectionManager {

    private String nickname;
    private int listenPort = 5000;
    private boolean e2eeEnabled = true;

    private TcpServer tcpServer;
    private UdpDiscoveryService discoveryService;
    private VoiceCallService voiceCallService;

    // Multi-peer connected map: Key = ip:port
    private final Map<String, PeerConnection> connectedPeers = new ConcurrentHashMap<>();
    private final List<Peer> onlinePeers = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    public ConnectionManager(String nickname, int listenPort) {
        this.nickname = nickname;
        this.listenPort = listenPort;
        this.voiceCallService = new VoiceCallService(listenPort + 1000);
        this.voiceCallService.start();
    }

    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getListenPort() { return listenPort; }
    public boolean isE2eeEnabled() { return e2eeEnabled; }
    public void setE2eeEnabled(boolean enabled) { this.e2eeEnabled = enabled; }
    public List<Peer> getOnlinePeers() { return new ArrayList<>(onlinePeers); }
    public Collection<PeerConnection> getConnectedPeers() { return connectedPeers.values(); }
    public VoiceCallService getVoiceCallService() { return voiceCallService; }

    public String getMyAddressKey() {
        return NetworkUtil.getPrimaryLocalIP() + ":" + listenPort;
    }

    /**
     * Start the background TCP Server and UDP Auto-Discovery.
     */
    public synchronized boolean startServer(int port) {
        this.listenPort = port;
        stopServer();

        try {
            tcpServer = new TcpServer(port, this::handleIncomingSocket, this::broadcastLog);
            tcpServer.start();

            // Start UDP Auto-Discovery service
            discoveryService = new UdpDiscoveryService(
                    nickname,
                    port,
                    voiceCallService.getLocalUdpPort(),
                    this::handlePeerDiscovered,
                    this::handlePeerLeft
            );
            discoveryService.start();

            return true;
        } catch (IOException e) {
            broadcastLog("Không thể khởi động ServerSocket tại cổng " + port + ": " + e.getMessage(), true);
            return false;
        }
    }

    public synchronized void stopServer() {
        if (discoveryService != null) {
            discoveryService.stop();
            discoveryService = null;
        }
        if (tcpServer != null) {
            tcpServer.stop();
            tcpServer = null;
        }
        for (PeerConnection conn : connectedPeers.values()) {
            conn.close();
        }
        connectedPeers.clear();
    }

    public boolean isServerListening() {
        return tcpServer != null && tcpServer.isListening();
    }

    /**
     * Handle incoming socket accepted by ServerSocket.
     */
    private void handleIncomingSocket(Socket socket) {
        try {
            PeerConnection conn = createPeerConnection(socket);
            conn.start();
        } catch (IOException e) {
            broadcastLog("Lỗi thiết lập phiên kết nối: " + e.getMessage(), true);
        }
    }

    /**
     * Connect manually or automatically to a peer IP:Port via TCP Socket.
     */
    public void connectToPeer(String ip, int port) {
        if (port == this.listenPort && (ip.equals("127.0.0.1") || ip.equalsIgnoreCase("localhost") || NetworkUtil.getLocalIPv4Addresses().contains(ip))) {
            broadcastLog("Không thể tự kết nối tới chính mình (" + ip + ":" + port + ")!", true);
            return;
        }

        String key = ip + ":" + port;
        if (connectedPeers.containsKey(key) && connectedPeers.get(key).isConnected()) {
            return; // Already connected
        }

        new Thread(() -> {
            try {
                Socket socket = new Socket(ip, port);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                PeerConnection conn = createPeerConnection(socket);
                conn.start();

                AudioNotifier.playConnectChime();
                broadcastLog("Kết nối thành công tới " + ip + ":" + port, false);

            } catch (IOException e) {
                broadcastLog("Không thể kết nối tới " + ip + ":" + port + ": " + e.getMessage(), true);
            }
        }, "ConnectThread-" + key).start();
    }

    /**
     * Handle automatic discovery of a peer on LAN.
     */
    private void handlePeerDiscovered(Peer peer) {
        peer.setAutoDiscovered(true);
        registerOrUpdatePeer(peer);

        // Auto-connect mesh logic (connect if not already connected and deterministic tie-breaker)
        String myKey = getMyAddressKey();
        String peerKey = peer.getIp() + ":" + peer.getPort();

        if (!connectedPeers.containsKey(peerKey) && myKey.compareTo(peerKey) < 0) {
            connectToPeer(peer.getIp(), peer.getPort());
        }
    }

    private void handlePeerLeft(Peer peer) {
        for (Peer p : onlinePeers) {
            if (p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort()) {
                p.setOnline(false);
                break;
            }
        }
        PeerConnection conn = connectedPeers.remove(peer.getAddressKey());
        if (conn != null) {
            conn.close();
        }
        for (ConnectionListener l : listeners) {
            l.onDisconnected(peer, peer.getNickname() + " đã rời mạng.");
        }
    }

    /**
     * Create PeerConnection with internal event routing.
     */
    private PeerConnection createPeerConnection(Socket socket) {
        final PeerConnection[] holder = new PeerConnection[1];
        holder[0] = new PeerConnection(socket, nickname, listenPort, new ConnectionListener() {
            @Override
            public void onConnected(Peer peer) {
                connectedPeers.put(peer.getAddressKey(), holder[0]);
                registerOrUpdatePeer(peer);
                for (ConnectionListener l : listeners) l.onConnected(peer);
            }

            @Override
            public void onDisconnected(Peer peer, String reason) {
                connectedPeers.remove(peer.getAddressKey());
                peer.setConnected(false);
                for (ConnectionListener l : listeners) l.onDisconnected(peer, reason);
            }

            @Override
            public void onMessageReceived(Peer peer, String text, boolean isEncrypted, boolean isGroup) {
                String plainText = text;
                if (isEncrypted) {
                    try {
                        plainText = SecurityUtil.decrypt(text);
                    } catch (Exception e) {
                        plainText = "[Không thể giải mã tin nhắn: " + e.getMessage() + "]";
                    }
                }
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onMessageReceived(peer, plainText, isEncrypted, isGroup);
            }

            @Override
            public void onVoiceNoteReceived(Peer peer, String fileName, int durationSeconds, byte[] audioData, boolean isGroup) {
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) {
                    l.onVoiceNoteReceived(peer, fileName, durationSeconds, audioData, isGroup);
                }
            }

            @Override
            public void onRoomInviteReceived(ChatRoom room, String invitedBy) {
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onRoomInviteReceived(room, invitedBy);
            }

            @Override
            public void onRoomRenamed(String roomId, String newRoomName, String updatedBy) {
                for (ConnectionListener l : listeners) l.onRoomRenamed(roomId, newRoomName, updatedBy);
            }

            @Override
            public void onRoomMembersUpdated(String roomId, List<String> newMembers, String updatedBy) {
                for (ConnectionListener l : listeners) l.onRoomMembersUpdated(roomId, newMembers, updatedBy);
            }

            @Override
            public void onRoomMessageReceived(String roomId, String senderNickname, String text, boolean isEncrypted) {
                String plainText = text;
                if (isEncrypted) {
                    try {
                        plainText = SecurityUtil.decrypt(text);
                    } catch (Exception e) {
                        plainText = "[Không thể giải mã: " + e.getMessage() + "]";
                    }
                }
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onRoomMessageReceived(roomId, senderNickname, plainText, isEncrypted);
            }

            @Override
            public void onRoomVoiceNoteReceived(String roomId, String senderNickname, String fileName, int durationSeconds, byte[] audioData) {
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onRoomVoiceNoteReceived(roomId, senderNickname, fileName, durationSeconds, audioData);
            }

            @Override
            public void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize) {
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onFileOfferReceived(peer, fileId, fileName, totalSize);
            }

            @Override
            public void onFileProgress(FileTransferTask task) {
                for (ConnectionListener l : listeners) l.onFileProgress(task);
            }

            @Override
            public void onFileCompleted(FileTransferTask task, boolean success, String message) {
                for (ConnectionListener l : listeners) l.onFileCompleted(task, success, message);
            }

            @Override
            public void onStatusLog(String message, boolean isError) {
                broadcastLog(message, isError);
            }

            @Override
            public void onCallOfferReceived(Peer peer, int remoteUdpPort) {
                voiceCallService.handleIncomingOffer(peer, remoteUdpPort);
                for (ConnectionListener l : listeners) l.onCallOfferReceived(peer, remoteUdpPort);
            }

            @Override
            public void onCallAccepted(Peer peer, int remoteUdpPort) {
                voiceCallService.onPeerAccepted(remoteUdpPort);
                for (ConnectionListener l : listeners) l.onCallAccepted(peer, remoteUdpPort);
            }

            @Override
            public void onCallRejected(Peer peer, String reason) {
                voiceCallService.hangup("Đối phương từ chối cuộc gọi");
                for (ConnectionListener l : listeners) l.onCallRejected(peer, reason);
            }

            @Override
            public void onCallHangup(Peer peer) {
                voiceCallService.hangup("Đối phương đã gác máy");
                for (ConnectionListener l : listeners) l.onCallHangup(peer);
            }
        });
        return holder[0];
    }

    public synchronized void registerOrUpdatePeer(Peer peer) {
        if (peer == null) return;
        // Do not register self
        if (peer.getPort() == this.listenPort && (peer.getIp().equals("127.0.0.1") || peer.getIp().equalsIgnoreCase("localhost") || NetworkUtil.getLocalIPv4Addresses().contains(peer.getIp()))) {
            return;
        }

        boolean found = false;
        for (Peer p : onlinePeers) {
            if (p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort()) {
                p.setNickname(peer.getNickname());
                p.setConnected(peer.isConnected());
                p.setOnline(true);
                p.setVoiceUdpPort(peer.getVoiceUdpPort());
                p.setAutoDiscovered(peer.isAutoDiscovered());
                p.setLastSeen(System.currentTimeMillis());
                found = true;
                break;
            }
        }
        if (!found) {
            onlinePeers.add(peer);
        }
    }

    /**
     * Broadcast text message to ALL connected peers (Multi-user Group Chat!).
     */
    public boolean broadcastTextMessage(String message) {
        if (connectedPeers.isEmpty()) {
            broadcastLog("Chưa kết nối với thiết bị nào trong phòng chat!", true);
            return false;
        }

        String payload = message;
        boolean encrypted = false;

        if (e2eeEnabled) {
            try {
                payload = SecurityUtil.encrypt(message);
                encrypted = true;
            } catch (Exception e) {
                broadcastLog("Lỗi mã hóa E2EE: " + e.getMessage(), true);
            }
        }

        boolean anySuccess = false;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected()) {
                boolean ok = conn.sendTextMessage(payload, encrypted, true);
                if (ok) anySuccess = true;
            }
        }
        return anySuccess;
    }

    /**
     * Send private text message to a specific peer.
     */
    public boolean sendPrivateMessage(Peer peer, String message) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn == null || !conn.isConnected()) {
            broadcastLog("Không có kết nối tới " + peer.getNickname(), true);
            return false;
        }

        String payload = message;
        boolean encrypted = false;
        if (e2eeEnabled) {
            try {
                payload = SecurityUtil.encrypt(message);
                encrypted = true;
            } catch (Exception ignored) {}
        }

        return conn.sendTextMessage(payload, encrypted, false);
    }

    public boolean broadcastVoiceNote(String fileName, int durationSeconds, byte[] audioData) {
        if (connectedPeers.isEmpty()) {
            broadcastLog("Chưa kết nối với thiết bị nào để gửi tin nhắn thoại!", true);
            return false;
        }
        boolean any = false;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected()) {
                if (conn.sendVoiceNote(fileName, durationSeconds, audioData, true)) {
                    any = true;
                }
            }
        }
        return any;
    }

    public boolean sendPrivateVoiceNote(Peer peer, String fileName, int durationSeconds, byte[] audioData) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn == null || !conn.isConnected()) {
            broadcastLog("Không có kết nối tới " + (peer != null ? peer.getNickname() : "đối phương"), true);
            return false;
        }
        return conn.sendVoiceNote(fileName, durationSeconds, audioData, false);
    }

    // ==========================================
    // Phase 5: Room Broadcasting Methods
    // ==========================================

    public boolean sendRoomTextMessage(ChatRoom room, String message) {
        if (room == null || message == null || message.trim().isEmpty()) return false;
        String payload = message;
        boolean encrypted = false;
        if (e2eeEnabled) {
            try {
                payload = SecurityUtil.encrypt(message);
                encrypted = true;
            } catch (Exception ignored) {}
        }

        boolean anySent = false;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && room.hasMember(conn.getPeer().getNickname())) {
                if (conn.sendRoomTextMessage(room.getId(), nickname, payload, encrypted)) {
                    anySent = true;
                }
            }
        }
        return anySent || !connectedPeers.isEmpty();
    }

    public boolean sendRoomVoiceNote(ChatRoom room, String fileName, int durationSeconds, byte[] audioData) {
        if (room == null || audioData == null) return false;
        boolean anySent = false;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && room.hasMember(conn.getPeer().getNickname())) {
                if (conn.sendRoomVoiceNote(room.getId(), nickname, fileName, durationSeconds, audioData)) {
                    anySent = true;
                }
            }
        }
        return anySent || !connectedPeers.isEmpty();
    }

    public void broadcastRoomInvite(ChatRoom room) {
        if (room == null) return;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && room.hasMember(conn.getPeer().getNickname())) {
                conn.sendRoomInvite(room);
            }
        }
    }

    public void sendRoomInviteToPeer(Peer peer, ChatRoom room) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn != null && conn.isConnected()) {
            conn.sendRoomInvite(room);
        }
    }

    public void broadcastRoomRename(ChatRoom room) {
        if (room == null) return;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && room.hasMember(conn.getPeer().getNickname())) {
                conn.sendRoomRename(room.getId(), room.getName(), nickname);
            }
        }
    }

    public void broadcastRoomMemberUpdate(ChatRoom room) {
        if (room == null) return;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && room.hasMember(conn.getPeer().getNickname())) {
                conn.sendRoomMemberUpdate(room.getId(), room.getMembers(), nickname);
            }
        }
    }

    /**
     * Send file to peer (or active peer).
     */
    public FileTransferTask sendFileToPeer(Peer peer, File file) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn == null || !conn.isConnected()) {
            // Fallback to first available peer
            if (!connectedPeers.isEmpty()) {
                conn = connectedPeers.values().iterator().next();
            }
        }

        if (conn == null || !conn.isConnected()) {
            broadcastLog("Chưa kết nối với thiết bị nào để gửi file!", true);
            return null;
        }
        return conn.offerFile(file);
    }

    public FileTransferTask acceptIncomingFile(Peer peer, String fileId, String fileName, long totalSize) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn == null && !connectedPeers.isEmpty()) {
            conn = connectedPeers.values().iterator().next();
        }
        if (conn != null) {
            return conn.acceptFileOffer(fileId, fileName, totalSize);
        }
        return null;
    }

    public void rejectIncomingFile(Peer peer, String fileId, String reason) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn != null) {
            conn.rejectFileOffer(fileId, reason);
        }
    }

    // ==========================================
    // Voice Call Coordination Methods
    // ==========================================

    public boolean callPeer(Peer peer) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn == null || !conn.isConnected()) {
            broadcastLog("Cần kết nối TCP trước khi gọi thoại tới " + peer.getNickname(), true);
            return false;
        }

        boolean ok = voiceCallService.initiateCall(peer);
        if (ok) {
            conn.sendCallOffer(voiceCallService.getLocalUdpPort());
        }
        return ok;
    }

    public void acceptIncomingCall(Peer peer, int remoteUdpPort) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn != null) {
            voiceCallService.acceptCall(peer, remoteUdpPort);
            conn.sendCallAccept(voiceCallService.getLocalUdpPort());
        }
    }

    public void rejectIncomingCall(Peer peer, String reason) {
        PeerConnection conn = getPeerConnectionByPeer(peer);
        if (conn != null) {
            conn.sendCallReject(reason);
        }
        voiceCallService.hangup(reason);
    }

    public void hangupCall() {
        Peer peer = voiceCallService.getCurrentCallPeer();
        if (peer != null) {
            PeerConnection conn = getPeerConnectionByPeer(peer);
            if (conn != null) {
                conn.sendCallHangup();
            }
        }
        voiceCallService.hangup("Người dùng kết thúc cuộc gọi");
    }

    public PeerConnection getPeerConnectionByPeer(Peer peer) {
        if (peer == null) return null;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.getPeer().getIp().equals(peer.getIp()) && conn.getPeer().getPort() == peer.getPort()) {
                return conn;
            }
        }
        return connectedPeers.get(peer.getAddressKey());
    }

    public boolean sendTextMessage(String message) {
        return broadcastTextMessage(message);
    }

    public FileTransferTask sendFile(File file) {
        return sendFileToPeer(null, file);
    }

    public void disconnectActiveSession() {
        disconnectAll();
    }

    public void disconnectAll() {
        for (PeerConnection conn : connectedPeers.values()) {
            conn.close();
        }
        connectedPeers.clear();
    }

    private void broadcastLog(String msg) {
        broadcastLog(msg, false);
    }

    private void broadcastLog(String msg, boolean isError) {
        for (ConnectionListener l : listeners) {
            l.onStatusLog(msg, isError);
        }
    }
}
