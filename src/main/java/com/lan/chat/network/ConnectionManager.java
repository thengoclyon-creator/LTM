package com.lan.chat.network;

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

    private final String nodeId = java.util.UUID.randomUUID().toString();
    private String nickname;
    private int listenPort = 5000;
    private boolean e2eeEnabled = true;

    private TcpServer tcpServer;
    private UdpDiscoveryService discoveryService;
    private VoiceCallService voiceCallService;

    // Multi-peer connected map: Key = peer.getAddressKey() (or nodeId)
    private final Map<String, PeerConnection> connectedPeers = new ConcurrentHashMap<>();
    private final List<Peer> onlinePeers = new CopyOnWriteArrayList<>();
    private final List<ConnectionListener> listeners = new CopyOnWriteArrayList<>();

    // LRU set to deduplicate chat messages by unique message ID
    private final java.util.Set<String> processedMessageIds = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<String, Boolean>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 1000;
                }
            }
    );

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

    public String getNodeId() { return nodeId; }
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
     * Automatically attempts consecutive ports if requested port is already in use.
     */
    public synchronized boolean startServer(int port) {
        stopServer();

        java.net.ServerSocket ss = null;
        int chosenPort = port;
        for (int offset = 0; offset <= 20; offset++) {
            int tryPort = port + offset;
            try {
                ss = new java.net.ServerSocket(tryPort);
                chosenPort = tryPort;
                break;
            } catch (IOException ignored) {
                // Port occupied, try next available port
            }
        }

        if (ss == null) {
            broadcastLog("Không thể khởi động ServerSocket tại cổng " + port + " (Tất cả cổng dự phòng đều bận)", true);
            return false;
        }

        this.listenPort = chosenPort;
        if (voiceCallService != null) {
            voiceCallService.stop();
        }
        this.voiceCallService = new VoiceCallService(chosenPort + 1000);
        this.voiceCallService.start();

        try {
            tcpServer = new TcpServer(ss, this::handleIncomingSocket, this::broadcastLog);
            tcpServer.start();

            // Start UDP Auto-Discovery service with unique nodeId
            discoveryService = new UdpDiscoveryService(
                    nodeId,
                    nickname,
                    chosenPort,
                    voiceCallService.getLocalUdpPort(),
                    this::handlePeerDiscovered,
                    this::handlePeerLeft
            );
            discoveryService.start();

            if (chosenPort != port) {
                broadcastLog("Cổng " + port + " đã có ứng dụng khác dùng. Đã tự động chuyển sang lắng nghe tại cổng " + chosenPort + "!", false);
            }

            return true;
        } catch (IOException e) {
            broadcastLog("Không thể khởi động ServerSocket tại cổng " + chosenPort + ": " + e.getMessage(), true);
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
        // Prevent connecting to self
        if ((ip.equals(NetworkUtil.getPrimaryLocalIP()) || ip.equals("127.0.0.1") || ip.equalsIgnoreCase("localhost")) && port == listenPort) {
            broadcastLog("Không thể tự kết nối tới chính mình (" + ip + ":" + port + ")! Vui lòng mở thêm 1 cửa sổ chat khác (hoặc máy khác) để kết nối.", true);
            return;
        }

        // Deduplicate: check if already connected to this peer
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && conn.getPeer().getIp().equals(ip) && conn.getPeer().getPort() == port) {
                broadcastLog("Đã có kết nối tới " + ip + ":" + port + " từ trước!", false);
                return;
            }
        }

        String key = ip + ":" + port;
        new Thread(() -> {
            try {
                Socket socket = new Socket(ip, port);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                PeerConnection conn = createPeerConnection(socket);
                conn.start();
                AudioNotifier.playConnectChime();
                broadcastLog("Đã thiết lập socket TCP tới " + ip + ":" + port + ", đang hoàn tất bắt tay...", false);

            } catch (IOException e) {
                broadcastLog("Không thể kết nối tới " + ip + ":" + port + ": " + e.getMessage(), true);
            }
        }, "ConnectThread-" + key).start();
    }

    /**
     * Handle automatic discovery of a peer on LAN.
     */
    private void handlePeerDiscovered(Peer peer) {
        if (nodeId.equals(peer.getId())) return;
        if (peer.getIp().equals(NetworkUtil.getPrimaryLocalIP()) && peer.getPort() == listenPort) return;

        peer.setAutoDiscovered(true);
        registerOrUpdatePeer(peer);

        // Auto-connect mesh logic: connect only if not already connected
        boolean alreadyConnected = false;
        for (PeerConnection conn : connectedPeers.values()) {
            if (conn.isConnected() && conn.getPeer().isSamePeer(peer)) {
                alreadyConnected = true;
                break;
            }
        }

        if (!alreadyConnected) {
            // Deterministic tie-breaker: node with smaller key initiates connection to avoid cross-connecting
            String myKey = nodeId;
            String peerKey = peer.getId() != null ? peer.getId() : (peer.getIp() + ":" + peer.getPort());
            if (myKey.compareTo(peerKey) < 0) {
                connectToPeer(peer.getIp(), peer.getPort());
            }
        }
    }

    private void handlePeerLeft(Peer peer) {
        for (Peer p : onlinePeers) {
            if (p.isSamePeer(peer)) {
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
     * Create PeerConnection with internal event routing and connection deduplication.
     */
    private PeerConnection createPeerConnection(Socket socket) {
        final PeerConnection[] holder = new PeerConnection[1];
        holder[0] = new PeerConnection(
                socket,
                nodeId,
                nickname,
                listenPort,
                voiceCallService.getLocalUdpPort(),
                new ConnectionListener() {
            @Override
            public void onConnected(Peer peer) {
                // 1. If connected to self, disconnect
                if (nodeId.equals(peer.getId())) {
                    holder[0].close();
                    return;
                }

                // 2. Connection deduplication: if already connected to this peer, close redundant connection
                PeerConnection existing = getPeerConnectionByPeer(peer);
                if (existing != null && existing != holder[0] && existing.isConnected()) {
                    holder[0].close();
                    return;
                }

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
            public void onMessageReceived(Peer peer, String text, boolean isEncrypted) {
                onMessageReceived(peer, java.util.UUID.randomUUID().toString(), text, isEncrypted);
            }

            @Override
            public void onMessageReceived(Peer peer, String messageId, String text, boolean isEncrypted) {
                // Deduplicate incoming message
                synchronized (processedMessageIds) {
                    if (messageId != null && !processedMessageIds.add(messageId)) {
                        return; // Duplicate message, ignore!
                    }
                }

                String plainText = text;
                if (isEncrypted) {
                    try {
                        plainText = SecurityUtil.decrypt(text);
                    } catch (Exception e) {
                        plainText = "[Không thể giải mã tin nhắn: " + e.getMessage() + "]";
                    }
                }
                AudioNotifier.playMessageTing();
                for (ConnectionListener l : listeners) l.onMessageReceived(peer, messageId, plainText, isEncrypted);
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
        if (nodeId.equals(peer.getId())) return;
        if (peer.getIp().equals(NetworkUtil.getPrimaryLocalIP()) && peer.getPort() == listenPort) return;

        boolean found = false;
        for (Peer p : onlinePeers) {
            if (p.isSamePeer(peer)) {
                p.setNickname(peer.getNickname());
                p.setConnected(peer.isConnected());
                p.setOnline(true);
                p.setVoiceUdpPort(peer.getVoiceUdpPort());
                p.setAutoDiscovered(peer.isAutoDiscovered());
                p.setLastSeen(System.currentTimeMillis());
                if (peer.getId() != null) p.setId(peer.getId());
                if (peer.getPort() > 0) p.setPort(peer.getPort());
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

        String messageId = java.util.UUID.randomUUID().toString();
        synchronized (processedMessageIds) {
            processedMessageIds.add(messageId);
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
                boolean ok = conn.sendTextMessage(messageId, payload, encrypted);
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

        String messageId = java.util.UUID.randomUUID().toString();
        synchronized (processedMessageIds) {
            processedMessageIds.add(messageId);
        }

        String payload = message;
        boolean encrypted = false;
        if (e2eeEnabled) {
            try {
                payload = SecurityUtil.encrypt(message);
                encrypted = true;
            } catch (Exception ignored) {}
        }

        return conn.sendTextMessage(messageId, payload, encrypted);
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
            if (conn.isConnected() && conn.getPeer().isSamePeer(peer)) {
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
