package com.lan.chat.ui;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.ChatTarget;
import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Message;
import com.lan.chat.model.Peer;
import com.lan.chat.network.ConnectionListener;
import com.lan.chat.network.ConnectionManager;
import com.lan.chat.network.PeerConnection;
import com.lan.chat.ui.components.ChatHistoryPanel;
import com.lan.chat.ui.components.FileTransferBar;
import com.lan.chat.ui.components.OnlinePeersPanel;
import com.lan.chat.ui.dialogs.CreateRoomDialog;
import com.lan.chat.ui.dialogs.ManageMembersDialog;
import com.lan.chat.util.AudioNotifier;
import com.lan.chat.util.NetworkUtil;
import com.lan.chat.storage.ChatStorageManager;
import com.lan.chat.voice.VoiceCallService;
import com.lan.chat.voice.VoicePlayer;
import com.lan.chat.voice.VoiceRecorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainChatFrame extends JFrame implements ConnectionListener {

    private final ConnectionManager connectionManager;
    private final ChatStorageManager chatStorageManager;
    private ChatTarget activeTarget = ChatTarget.allLan();
    private static final String GROUP_CHANNEL = "GROUP";
    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();

    // Header Controls
    private JLabel lblMyIp;
    private JTextField txtLocalPort;
    private JButton btnToggleServer;
    private JTextField txtTargetIp;
    private JTextField txtTargetPort;
    private JButton btnConnect;
    private JCheckBox chkE2ee;

    // Content Components
    private OnlinePeersPanel peersPanel;
    private ChatHistoryPanel chatHistoryPanel;
    private FileTransferBar fileTransferBar;
    private JLabel lblActivePeerStatus;
    private JButton btnRenameRoom;
    private JButton btnManageMembers;

    public Peer getActivePeer() {
        return activeTarget != null && activeTarget.isPeer() ? activeTarget.getPeer() : null;
    }

    public ChatRoom getActiveRoom() {
        return activeTarget != null && activeTarget.isRoom() ? activeTarget.getRoom() : null;
    }

    // Voice Call Controls
    private JButton btnCall;
    private JButton btnHangup;
    private JButton btnMute;
    private JLabel lblCallBanner;

    // Voice Note Service & Controls
    private final VoiceRecorder voiceRecorder = new VoiceRecorder();
    private final VoicePlayer voicePlayer = new VoicePlayer();
    private static final String VOICE_DIR = "voice_notes";
    private String currentlyPlayingAudio = null;
    private JButton btnVoiceRecord;
    private JButton btnCancelRecord;

    // Input Controls
    private JTextField txtMessageInput;
    private JButton btnSend;
    private JButton btnSelectFile;

    public MainChatFrame(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.connectionManager.addListener(this);
        this.chatStorageManager = new ChatStorageManager(connectionManager.getNickname());

        initWindow();
        initHeaderPanel();
        initCenterPanel();
        initInputPanel();
        initVoiceCallbacks();

        loadSavedHistories();

        refreshCurrentConversationView();

        // Default startup: Automatically launch local server & UDP Auto-Discovery
        startLocalServer();
    }

    private void initWindow() {
        setTitle("LAN Chat Pro - Chat Nhóm & Voice UDP Đa Thiết Bị");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1060, 720);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        getContentPane().setBackground(new Color(248, 250, 252));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (voiceRecorder.isRecording()) {
                    voiceRecorder.cancelRecording();
                }
                voicePlayer.stop();
            }
        });
    }

    private void initHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        // Left of Header: App Brand, Local IP & Server Listening
        JPanel localInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        localInfoPanel.setOpaque(false);

        JLabel logo = new JLabel("LAN CHAT PRO");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(new Color(67, 56, 202));

        String myIp = NetworkUtil.getPrimaryLocalIP();
        lblMyIp = new JLabel("IP: " + myIp);
        lblMyIp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMyIp.setForeground(new Color(71, 85, 105));

        JLabel lblPort = new JLabel("Port:");
        lblPort.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPort.setForeground(new Color(100, 116, 139));

        txtLocalPort = new JTextField(String.valueOf(connectionManager.getListenPort()), 4);
        txtLocalPort.setHorizontalAlignment(JTextField.CENTER);
        txtLocalPort.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        btnToggleServer = new JButton("Khởi động");
        btnToggleServer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnToggleServer.setFocusPainted(false);
        btnToggleServer.addActionListener(e -> toggleServer());

        localInfoPanel.add(logo);
        localInfoPanel.add(Box.createHorizontalStrut(6));
        localInfoPanel.add(lblMyIp);
        localInfoPanel.add(lblPort);
        localInfoPanel.add(txtLocalPort);
        localInfoPanel.add(btnToggleServer);

        // Right of Header: Manual TCP Connection & E2EE Checkbox
        JPanel remoteConnectPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        remoteConnectPanel.setOpaque(false);

        JLabel lblRemote = new JLabel("Kết nối IP:");
        lblRemote.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRemote.setForeground(new Color(100, 116, 139));

        txtTargetIp = new JTextField(myIp, 10);
        txtTargetIp.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        txtTargetPort = new JTextField("5000", 4);
        txtTargetPort.setHorizontalAlignment(JTextField.CENTER);
        txtTargetPort.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        btnConnect = new JButton("Nối TCP");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnConnect.setBackground(new Color(79, 70, 229));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> connectToTarget());

        chkE2ee = new JCheckBox("Mã hóa AES (E2EE)", true);
        chkE2ee.setOpaque(false);
        chkE2ee.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chkE2ee.setForeground(new Color(5, 150, 105));
        chkE2ee.setToolTipText("Mã hóa tin nhắn End-to-End bằng AES-GCM (Chống soi gói tin trên mạng LAN)");
        chkE2ee.addActionListener(e -> connectionManager.setE2eeEnabled(chkE2ee.isSelected()));

        remoteConnectPanel.add(lblRemote);
        remoteConnectPanel.add(txtTargetIp);
        remoteConnectPanel.add(txtTargetPort);
        remoteConnectPanel.add(btnConnect);
        remoteConnectPanel.add(Box.createHorizontalStrut(8));
        remoteConnectPanel.add(chkE2ee);

        header.add(localInfoPanel, BorderLayout.WEST);
        header.add(remoteConnectPanel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void initCenterPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        // Left sidebar: Online devices list with Group Chat support
        peersPanel = new OnlinePeersPanel();
        peersPanel.setOnTargetSelected(this::switchChatTarget);
        peersPanel.setOnCreateRoomRequested(this::showCreateRoomDialog);
        peersPanel.setOnRescanRequested(() -> {
            connectionManager.triggerRescan();
            chatHistoryPanel.appendSystem("🔄 Đang phát tín hiệu tìm kiếm thiết bị trong mạng LAN...", false);
        });
        peersPanel.setOnPeerConnectRequest(peer -> {
            txtTargetIp.setText(peer.getIp());
            txtTargetPort.setText(String.valueOf(peer.getPort()));
            connectToTarget();
        });

        // Chat Container
        JPanel chatContainer = new JPanel(new BorderLayout());
        chatContainer.setOpaque(false);

        // Subheader: Active target and Voice Call toolbar
        JPanel subHeaderPanel = new JPanel(new BorderLayout(8, 0));
        subHeaderPanel.setBackground(Color.WHITE);
        subHeaderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        lblActivePeerStatus = new JLabel("PHÒNG CHAT CHUNG - Gửi tin tới tất cả thiết bị LAN");
        lblActivePeerStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblActivePeerStatus.setForeground(new Color(15, 23, 42));

        // Voice call action buttons
        JPanel voiceActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        voiceActions.setOpaque(false);

        btnRenameRoom = new JButton("✏️ Đổi tên");
        btnRenameRoom.setToolTipText("Đổi tên nhóm này");
        btnRenameRoom.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRenameRoom.setBackground(new Color(241, 245, 249));
        btnRenameRoom.setForeground(new Color(67, 56, 202));
        btnRenameRoom.setFocusPainted(false);
        btnRenameRoom.setVisible(false);
        btnRenameRoom.addActionListener(e -> showRenameRoomDialog());

        btnManageMembers = new JButton("👥 Thành viên");
        btnManageMembers.setToolTipText("Xem & quản lý thành viên trong nhóm");
        btnManageMembers.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnManageMembers.setBackground(new Color(241, 245, 249));
        btnManageMembers.setForeground(new Color(5, 150, 105));
        btnManageMembers.setFocusPainted(false);
        btnManageMembers.setVisible(false);
        btnManageMembers.addActionListener(e -> showManageMembersDialog());

        lblCallBanner = new JLabel("");
        lblCallBanner.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblCallBanner.setForeground(new Color(5, 150, 105));
        lblCallBanner.setVisible(false);

        btnCall = new JButton("Gọi thoại");
        btnCall.setToolTipText("Gọi thoại trực tiếp qua UDP tới đối phương");
        btnCall.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCall.setBackground(new Color(16, 185, 129));
        btnCall.setForeground(Color.WHITE);
        btnCall.setFocusPainted(false);
        btnCall.addActionListener(e -> initiateVoiceCall());

        btnHangup = new JButton("Ngắt cuộc gọi");
        btnHangup.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnHangup.setBackground(new Color(220, 38, 38));
        btnHangup.setForeground(Color.WHITE);
        btnHangup.setFocusPainted(false);
        btnHangup.setVisible(false);
        btnHangup.addActionListener(e -> connectionManager.hangupCall());

        btnMute = new JButton("Mic: BẬT");
        btnMute.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnMute.setFocusPainted(false);
        btnMute.setVisible(false);
        btnMute.addActionListener(e -> toggleMute());

        JButton btnClearHistory = new JButton("Xóa lịch sử");
        btnClearHistory.setToolTipText("Xóa lịch sử tin nhắn của cuộc trò chuyện này");
        btnClearHistory.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearHistory.setBackground(new Color(241, 245, 249));
        btnClearHistory.setForeground(new Color(100, 116, 139));
        btnClearHistory.setFocusPainted(false);
        btnClearHistory.addActionListener(e -> clearCurrentConversation());

        voiceActions.add(btnRenameRoom);
        voiceActions.add(btnManageMembers);
        voiceActions.add(btnClearHistory);
        voiceActions.add(lblCallBanner);
        voiceActions.add(btnCall);
        voiceActions.add(btnMute);
        voiceActions.add(btnHangup);

        subHeaderPanel.add(lblActivePeerStatus, BorderLayout.WEST);
        subHeaderPanel.add(voiceActions, BorderLayout.EAST);

        chatHistoryPanel = new ChatHistoryPanel();
        chatHistoryPanel.setOnAudioPlayRequested(this::handlePlayVoiceNote);

        // Upgraded FileTransferBar with Resume and Pause actions
        fileTransferBar = new FileTransferBar();
        fileTransferBar.setOnPauseResumeAction(task -> {
            PeerConnection conn = connectionManager.getPeerConnectionByPeer(getActivePeer());
            if (conn != null) {
                if (task.getStatus() == FileTransferTask.Status.PAUSED) {
                    conn.resumeFileTransfer(task.getFileId());
                    chatHistoryPanel.appendSystem("Yêu cầu tiếp tục truyền file: " + task.getFileName(), false);
                } else {
                    conn.pauseFileTransfer(task.getFileId());
                    chatHistoryPanel.appendSystem("Đã tạm dừng truyền file: " + task.getFileName(), false);
                }
            }
        });
        fileTransferBar.setOnCancelAction(task -> {
            fileTransferBar.transferFinished(false, "Người dùng đã hủy");
            chatHistoryPanel.appendSystem("Đã hủy truyền file: " + task.getFileName(), true);
        });

        chatContainer.add(subHeaderPanel, BorderLayout.NORTH);
        chatContainer.add(chatHistoryPanel, BorderLayout.CENTER);
        chatContainer.add(fileTransferBar, BorderLayout.SOUTH);

        // Split Pane (Sidebar left, Chat right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, peersPanel, chatContainer);
        splitPane.setDividerLocation(260);
        splitPane.setDividerSize(4);
        splitPane.setBorder(BorderFactory.createEmptyBorder());

        center.add(splitPane, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
    }

    private void initInputPanel() {
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(Color.WHITE);
        bottomContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(8, 14, 10, 14)
        ));

        // Emoji Toolbar (Configured with Segoe UI Emoji font so they render cleanly!)
        JPanel emojiToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        emojiToolbar.setOpaque(false);

        JLabel lblEmoji = new JLabel("Biểu tượng:");
        lblEmoji.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblEmoji.setForeground(new Color(100, 116, 139));
        emojiToolbar.add(lblEmoji);

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 15);
        String[] emojis = {"😊", "👍", "❤️", "🔥", "🎉", "🚀", "👏", "😂", "⭐", "💻"};
        for (String emo : emojis) {
            JButton btnEmo = new JButton(emo);
            btnEmo.setFont(emojiFont);
            btnEmo.setMargin(new Insets(1, 4, 1, 4));
            btnEmo.setFocusPainted(false);
            btnEmo.setContentAreaFilled(false);
            btnEmo.setBorderPainted(false);
            btnEmo.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btnEmo.addActionListener(e -> {
                txtMessageInput.setText(txtMessageInput.getText() + emo);
                txtMessageInput.requestFocusInWindow();
            });
            emojiToolbar.add(btnEmo);
        }

        // Action & Text Input Panel
        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        btnSelectFile = new JButton("📁 File");
        btnSelectFile.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSelectFile.setFocusPainted(false);
        btnSelectFile.setBackground(Color.WHITE);
        btnSelectFile.setForeground(new Color(51, 65, 85));
        btnSelectFile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        btnSelectFile.addActionListener(e -> chooseAndSendFile());

        btnVoiceRecord = new JButton("🎙️ Thu âm");
        btnVoiceRecord.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnVoiceRecord.setFocusPainted(false);
        btnVoiceRecord.setBackground(Color.WHITE);
        btnVoiceRecord.setForeground(new Color(67, 56, 202));
        btnVoiceRecord.setToolTipText("Bấm để bắt đầu thu âm voice. Bấm lại để gửi.");
        btnVoiceRecord.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        btnVoiceRecord.addActionListener(e -> toggleVoiceRecording());

        btnCancelRecord = new JButton("❌ Hủy");
        btnCancelRecord.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancelRecord.setFocusPainted(false);
        btnCancelRecord.setBackground(Color.WHITE);
        btnCancelRecord.setForeground(new Color(220, 38, 38));
        btnCancelRecord.setVisible(false);
        btnCancelRecord.setToolTipText("Hủy bỏ bản ghi âm này");
        btnCancelRecord.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(254, 202, 202), 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        btnCancelRecord.addActionListener(e -> cancelVoiceRecording());

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftActions.setOpaque(false);
        leftActions.add(btnSelectFile);
        leftActions.add(btnVoiceRecord);
        leftActions.add(btnCancelRecord);

        txtMessageInput = new JTextField();
        // Crucial: Use Segoe UI Emoji font so emojis don't display as tofu boxes!
        txtMessageInput.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        txtMessageInput.setBackground(Color.WHITE);
        txtMessageInput.setForeground(new Color(15, 23, 42));
        txtMessageInput.setCaretColor(new Color(15, 23, 42));
        txtMessageInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        txtMessageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });

        btnSend = new JButton("Gửi");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSend.setBackground(new Color(79, 70, 229));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        btnSend.addActionListener(e -> sendMessage());

        inputRow.add(leftActions, BorderLayout.WEST);
        inputRow.add(txtMessageInput, BorderLayout.CENTER);
        inputRow.add(btnSend, BorderLayout.EAST);

        bottomContainer.add(emojiToolbar, BorderLayout.NORTH);
        bottomContainer.add(inputRow, BorderLayout.SOUTH);

        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void initVoiceCallbacks() {
        connectionManager.getVoiceCallService().setCallbacks(
                state -> SwingUtilities.invokeLater(() -> updateVoiceUiState(state)),
                (peer, remotePort) -> SwingUtilities.invokeLater(() -> handleIncomingVoiceCall(peer, remotePort)),
                log -> SwingUtilities.invokeLater(() -> chatHistoryPanel.appendSystem(log, false))
        );
    }

    private void updateVoiceUiState(VoiceCallService.CallState state) {
        switch (state) {
            case IDLE -> {
                btnCall.setVisible(true);
                btnCall.setEnabled(true);
                btnHangup.setVisible(false);
                btnMute.setVisible(false);
                lblCallBanner.setVisible(false);
            }
            case CALLING -> {
                btnCall.setEnabled(false);
                btnHangup.setVisible(true);
                lblCallBanner.setText("Đang đổ chuông...");
                lblCallBanner.setForeground(new Color(217, 119, 6));
                lblCallBanner.setVisible(true);
            }
            case RINGING -> {
                lblCallBanner.setText("Cuộc gọi đến...");
                lblCallBanner.setForeground(new Color(217, 119, 6));
                lblCallBanner.setVisible(true);
            }
            case CONNECTED -> {
                btnCall.setVisible(false);
                btnHangup.setVisible(true);
                btnMute.setVisible(true);
                lblCallBanner.setText("Đang gọi thoại (UDP)");
                lblCallBanner.setForeground(new Color(5, 150, 105));
                lblCallBanner.setVisible(true);
            }
        }
    }

    private void toggleMute() {
        boolean newMuted = !connectionManager.getVoiceCallService().isMuted();
        connectionManager.getVoiceCallService().setMuted(newMuted);
        btnMute.setText(newMuted ? "Mic: TẮT" : "Mic: BẬT");
        btnMute.setForeground(newMuted ? new Color(220, 38, 38) : null);
    }

    private void loadSavedHistories() {
        // 1. Preload General Group Chat messages
        List<Message> groupMsgs = chatStorageManager.loadMessages(GROUP_CHANNEL);
        conversationHistories.put(GROUP_CHANNEL, new CopyOnWriteArrayList<>(groupMsgs));

        // 2. Preload custom group rooms
        List<ChatRoom> savedRooms = chatStorageManager.loadRooms();
        for (ChatRoom room : savedRooms) {
            String channelKey = "ROOM_" + room.getId();
            List<Message> msgs = chatStorageManager.loadMessages(channelKey);
            conversationHistories.put(channelKey, new CopyOnWriteArrayList<>(msgs));
            peersPanel.addOrUpdateRoom(room);
        }

        // 3. Preload past peer conversations (Zalo/Messenger style)
        List<String> savedPeers = chatStorageManager.getSavedPeerKeys();
        for (String peerName : savedPeers) {
            String channelKey = "PEER_" + peerName;
            List<Message> msgs = chatStorageManager.loadMessages(channelKey);
            conversationHistories.put(channelKey, new CopyOnWriteArrayList<>(msgs));

            // Add peer to sidebar as offline/recent contact
            Peer recentPeer = new Peer("0.0.0.0", 0, peerName);
            recentPeer.setOnline(false);
            recentPeer.setConnected(false);
            peersPanel.addOrUpdatePeer(recentPeer);
        }
    }

    private String getChannelKeyForPeer(Peer peer) {
        if (peer == null) return GROUP_CHANNEL;
        String name = peer.getNickname();
        if (name == null || name.trim().isEmpty() || name.equals(peer.getIp() + ":" + peer.getPort())) {
            return "PEER_" + ChatStorageManager.sanitizeFileName(peer.getAddressKey());
        }
        return "PEER_" + ChatStorageManager.sanitizeFileName(name);
    }

    private String getActiveChannelKey() {
        return activeTarget != null ? activeTarget.getKey() : GROUP_CHANNEL;
    }

    private void clearCurrentConversation() {
        String targetName = activeTarget.getDisplayName();
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn xóa toàn bộ lịch sử tin nhắn của '" + targetName + "'?",
                "Xác nhận xóa lịch sử",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
            String channelKey = getActiveChannelKey();
            List<Message> history = conversationHistories.get(channelKey);
            if (history != null) {
                history.clear();
            }
            chatStorageManager.clearHistory(channelKey);
            refreshCurrentConversationView();
        }
    }

    private void switchChatTarget(ChatTarget target) {
        this.activeTarget = target != null ? target : ChatTarget.allLan();
        if (activeTarget.isPeer()) {
            Peer peer = activeTarget.getPeer();
            peer.clearUnread();
            peersPanel.refreshList();

            // Auto-connect if discovered online but not yet connected
            if (!peer.isConnected() && peer.isOnline()) {
                connectionManager.connectToPeer(peer.getIp(), peer.getPort());
            }
        } else if (activeTarget.isRoom()) {
            ChatRoom room = activeTarget.getRoom();
            room.clearUnread();
            peersPanel.refreshList();
        }
        updateChatTargetBanner();
        refreshCurrentConversationView();
    }

    private void refreshCurrentConversationView() {
        String channelKey = getActiveChannelKey();
        List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
        String notice;
        if (activeTarget.isAllLan()) {
            notice = "📢 PHÒNG CHAT CHUNG LAN: Gửi tin tới tất cả thiết bị đang online.";
        } else if (activeTarget.isRoom()) {
            ChatRoom r = activeTarget.getRoom();
            notice = "👥 NHÓM: " + r.getName() + " (" + r.getMembers().size() + " thành viên: " + r.getMembersSummary() + ")";
        } else {
            Peer p = activeTarget.getPeer();
            notice = "🔒 CHAT RIÊNG VỚI " + p.getNickname() + " (" + p.getAddressKey() + "). Cuộc trò chuyện riêng tư 1-1.";
        }
        chatHistoryPanel.renderConversation(history, notice);
    }

    private void updateChatTargetBanner() {
        if (activeTarget.isAllLan()) {
            lblActivePeerStatus.setText("PHÒNG CHAT CHUNG - Gửi tin tới tất cả thiết bị LAN");
            lblActivePeerStatus.setForeground(new Color(15, 23, 42));
            btnCall.setVisible(false);
            btnRenameRoom.setVisible(false);
            btnManageMembers.setVisible(false);
        } else if (activeTarget.isRoom()) {
            ChatRoom r = activeTarget.getRoom();
            lblActivePeerStatus.setText("NHÓM: " + r.getName() + " • " + r.getMembers().size() + " thành viên (" + r.getMembersSummary() + ")");
            lblActivePeerStatus.setForeground(new Color(5, 150, 105));
            btnCall.setVisible(false);
            btnRenameRoom.setVisible(true);
            btnManageMembers.setVisible(true);
            btnManageMembers.setText("👥 Thành viên (" + r.getMembers().size() + ")");
        } else {
            Peer p = activeTarget.getPeer();
            String connStatus;
            if (p.isConnected()) {
                connStatus = "Đã nối trực tiếp 1-1";
            } else if (p.isOnline()) {
                connStatus = "Đang kết nối...";
            } else {
                connStatus = "Ngoại tuyến (Offline)";
            }
            lblActivePeerStatus.setText("CHAT RIÊNG: " + p.getNickname() + " (" + p.getAddressKey() + ") • " + connStatus);
            lblActivePeerStatus.setForeground(new Color(67, 56, 202));
            btnCall.setVisible(true);
            btnRenameRoom.setVisible(false);
            btnManageMembers.setVisible(false);
        }
    }

    // ==========================================
    // Event Actions
    // ==========================================

    private void toggleServer() {
        if (connectionManager.isServerListening()) {
            connectionManager.stopServer();
            btnToggleServer.setText("Khởi động");
            btnToggleServer.setBackground(null);
            btnToggleServer.setForeground(null);
            txtLocalPort.setEnabled(true);
            chatHistoryPanel.appendSystem("Đã dừng Server.", false);
        } else {
            startLocalServer();
        }
    }

    private void startLocalServer() {
        try {
            int port = Integer.parseInt(txtLocalPort.getText().trim());
            boolean ok = connectionManager.startServer(port);
            if (ok) {
                btnToggleServer.setText("Dừng Server");
                btnToggleServer.setBackground(new Color(16, 185, 129));
                btnToggleServer.setForeground(Color.WHITE);
                txtLocalPort.setEnabled(false);
                chatHistoryPanel.appendSystem("ServerSocket & Auto-Discovery UDP đã bật tại cổng " + port, false);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng Port phải là số nguyên (VD: 5000)", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void connectToTarget() {
        String targetIp = txtTargetIp.getText().trim();
        String portStr = txtTargetPort.getText().trim();

        if (targetIp.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập IP đích!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int targetPort = Integer.parseInt(portStr);
            chatHistoryPanel.appendSystem("Đang kết nối TCP tới " + targetIp + ":" + targetPort + "...", false);
            connectionManager.connectToPeer(targetIp, targetPort);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port đích không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMessage() {
        String text = txtMessageInput.getText().trim();
        if (text.isEmpty()) return;

        boolean sent;
        String channelKey;
        String myPrefix;

        if (activeTarget.isAllLan()) {
            // Group Chat: Broadcast to all connected peers
            sent = connectionManager.broadcastTextMessage(text);
            channelKey = GROUP_CHANNEL;
            myPrefix = "Bạn [Chung]";
        } else if (activeTarget.isRoom()) {
            // Custom Private Group Room
            ChatRoom room = activeTarget.getRoom();
            sent = connectionManager.sendRoomTextMessage(room, text);
            channelKey = activeTarget.getKey();
            myPrefix = "Bạn [" + room.getName() + "]";
        } else {
            // Private 1-1 Chat: Send to specific target peer
            Peer p = activeTarget.getPeer();
            sent = connectionManager.sendPrivateMessage(p, text);
            channelKey = activeTarget.getKey();
            myPrefix = "Bạn [Chat riêng]";
        }

        if (sent) {
            Message msg = Message.userText(myPrefix, text, true, connectionManager.isE2eeEnabled());
            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            }
            txtMessageInput.setText("");
        } else {
            chatHistoryPanel.appendSystem("Chưa thể gửi tin nhắn! Hãy đảm bảo đã kết nối tới thiết bị đối phương.", true);
        }
    }

    private void chooseAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file để gửi qua mạng LAN (Hỗ trợ GB & Resume)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file != null && file.exists()) {
                FileTransferTask task = connectionManager.sendFileToPeer(getActivePeer(), file);
                if (task != null) {
                    fileTransferBar.updateTask(task);
                    chatHistoryPanel.appendSystem("Đã gửi lời mời truyền file: " + file.getName()
                            + " (" + NetworkUtil.formatBytes(file.length()) + ")", false);
                }
            }
        }
    }

    private void initiateVoiceCall() {
        Peer target = getActivePeer();
        if (target == null) {
            if (!connectionManager.getConnectedPeers().isEmpty()) {
                target = connectionManager.getConnectedPeers().iterator().next().getPeer();
            }
        }

        if (target == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 thiết bị 1-1 bên trái để bắt đầu cuộc gọi thoại!",
                    "Chưa chọn đối phương", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        boolean ok = connectionManager.callPeer(target);
        if (!ok) {
            chatHistoryPanel.appendSystem("Không thể khởi tạo cuộc gọi thoại tới " + target.getNickname(), true);
        }
    }

    private void handleIncomingVoiceCall(Peer peer, int remoteUdpPort) {
        AudioNotifier.playMessageTing();
        int choice = JOptionPane.showConfirmDialog(
                this,
                peer.getNickname() + " (" + peer.getIp() + ") đang gọi thoại trực tiếp qua UDP!\nBạn có muốn nghe máy không?",
                "Cuộc gọi thoại mạng LAN",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            connectionManager.acceptIncomingCall(peer, remoteUdpPort);
        } else {
            connectionManager.rejectIncomingCall(peer, "Người nhận bận");
        }
    }

    // ==========================================
    // ConnectionListener Callbacks
    // ==========================================

    @Override
    public void onPeerDiscovered(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            peersPanel.addOrUpdatePeer(peer);
        });
    }

    @Override
    public void onConnected(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            chatHistoryPanel.appendSystem("Kết nối TCP thành công với " + peer.getNickname() + " [" + peer.getIp() + "]", false);
            peersPanel.addOrUpdatePeer(peer);
            updateChatTargetBanner();
        });
    }

    @Override
    public void onDisconnected(Peer peer, String reason) {
        SwingUtilities.invokeLater(() -> {
            chatHistoryPanel.appendSystem(reason, true);
            peersPanel.addOrUpdatePeer(peer);
        });
    }

    @Override
    public void onMessageReceived(Peer peer, String text, boolean isEncrypted, boolean isGroup) {
        SwingUtilities.invokeLater(() -> {
            String channelKey;
            Message msg;

            if (isGroup) {
                channelKey = GROUP_CHANNEL;
                msg = Message.userText(peer.getNickname() + " [Chung]", text, false, isEncrypted);
            } else {
                channelKey = getChannelKeyForPeer(peer);
                msg = Message.userText(peer.getNickname() + " [Chat riêng]", text, false, isEncrypted);
            }

            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            } else {
                if (!isGroup) {
                    peer.incrementUnread();
                    peersPanel.refreshList();
                }
            }
        });
    }

    @Override
    public void onMessageReceived(Peer peer, String text, boolean isEncrypted) {
        onMessageReceived(peer, text, isEncrypted, true);
    }

    @Override
    public void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize) {
        SwingUtilities.invokeLater(() -> {
            String sizeStr = NetworkUtil.formatBytes(totalSize);
            File downloadDir = new File(System.getProperty("user.home"), "Downloads");
            File partFile = new File(downloadDir, fileName + ".part");
            String resumeInfo = "";
            if (partFile.exists() && partFile.length() > 0) {
                resumeInfo = "\n(Phát hiện file dở dang: " + NetworkUtil.formatBytes(partFile.length()) + " -> Sẽ tự động Nối tiếp!)";
            }

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Đối phương " + peer.getNickname() + " gửi file:\n"
                            + fileName + " (" + sizeStr + ")" + resumeInfo + "\nBạn có muốn nhận không?",
                    "Yêu cầu nhận file qua LAN",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                FileTransferTask task = connectionManager.acceptIncomingFile(peer, fileId, fileName, totalSize);
                if (task != null) {
                    fileTransferBar.updateTask(task);
                    chatHistoryPanel.appendSystem("Bắt đầu tải file: " + fileName, false);
                }
            } else {
                connectionManager.rejectIncomingFile(peer, fileId, "Người nhận đã từ chối.");
                chatHistoryPanel.appendSystem("Đã từ chối nhận file: " + fileName, false);
            }
        });
    }

    @Override
    public void onFileProgress(FileTransferTask task) {
        SwingUtilities.invokeLater(() -> fileTransferBar.updateTask(task));
    }

    @Override
    public void onFileCompleted(FileTransferTask task, boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            fileTransferBar.transferFinished(success, message);
            chatHistoryPanel.appendSystem(message, !success);
        });
    }

    @Override
    public void onStatusLog(String message, boolean isError) {
        SwingUtilities.invokeLater(() -> chatHistoryPanel.appendSystem(message, isError));
    }

    @Override
    public void onCallOfferReceived(Peer peer, int remoteUdpPort) {
        // Handled in voice callback
    }

    @Override
    public void onCallAccepted(Peer peer, int remoteUdpPort) {
        // Handled in voice callback
    }

    @Override
    public void onCallRejected(Peer peer, String reason) {
        SwingUtilities.invokeLater(() -> {
            chatHistoryPanel.appendSystem("Cuộc gọi bị từ chối: " + reason, true);
        });
    }

    @Override
    public void onCallHangup(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            chatHistoryPanel.appendSystem("Đối phương đã gác máy.", false);
        });
    }

    // ==========================================
    // Voice Note Recording & Playback Actions
    // ==========================================

    private void toggleVoiceRecording() {
        if (!voiceRecorder.isRecording()) {
            boolean started = voiceRecorder.startRecording(seconds -> {
                SwingUtilities.invokeLater(() -> {
                    int m = seconds / 60;
                    int s = seconds % 60;
                    btnVoiceRecord.setText(String.format("⏹ Dừng & Gửi (%02d:%02d)", m, s));
                });
            });

            if (started) {
                btnVoiceRecord.setText("⏹ Dừng & Gửi (00:00)");
                btnVoiceRecord.setBackground(new Color(220, 38, 38));
                btnVoiceRecord.setForeground(Color.WHITE);
                btnCancelRecord.setVisible(true);
                txtMessageInput.setEnabled(false);
                btnSelectFile.setEnabled(false);
                chatHistoryPanel.appendSystem("🎙️ Đang ghi âm tin nhắn thoại... Bấm 'Dừng & Gửi' để gửi đi.", false);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Không thể khởi động ghi âm microphone! Hãy kiểm tra cài đặt Micro của hệ điều hành.",
                        "Lỗi Microphone", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            VoiceRecorder.RecordResult result = voiceRecorder.stopRecording();
            resetVoiceRecordUi();

            if (result == null || result.audioBytes() == null || result.audioBytes().length == 0) {
                chatHistoryPanel.appendSystem("Tin nhắn thoại quá ngắn hoặc không có âm thanh.", false);
                return;
            }

            sendVoiceNote(result);
        }
    }

    private void cancelVoiceRecording() {
        if (voiceRecorder.isRecording()) {
            voiceRecorder.cancelRecording();
            resetVoiceRecordUi();
            chatHistoryPanel.appendSystem("Đã hủy bản ghi âm.", false);
        }
    }

    private void resetVoiceRecordUi() {
        btnVoiceRecord.setText("🎙️ Thu âm");
        btnVoiceRecord.setBackground(Color.WHITE);
        btnVoiceRecord.setForeground(new Color(67, 56, 202));
        btnCancelRecord.setVisible(false);
        txtMessageInput.setEnabled(true);
        btnSelectFile.setEnabled(true);
    }

    private void sendVoiceNote(VoiceRecorder.RecordResult result) {
        String fileName = result.file().getName();
        int duration = result.durationSeconds();
        byte[] audioBytes = result.audioBytes();

        boolean sent;
        String channelKey;
        String myPrefix;

        if (activeTarget.isAllLan()) {
            sent = connectionManager.broadcastVoiceNote(fileName, duration, audioBytes);
            channelKey = GROUP_CHANNEL;
            myPrefix = "Bạn [Chung]";
        } else if (activeTarget.isRoom()) {
            ChatRoom room = activeTarget.getRoom();
            sent = connectionManager.sendRoomVoiceNote(room, fileName, duration, audioBytes);
            channelKey = activeTarget.getKey();
            myPrefix = "Bạn [" + room.getName() + "]";
        } else {
            Peer p = activeTarget.getPeer();
            sent = connectionManager.sendPrivateVoiceNote(p, fileName, duration, audioBytes);
            channelKey = activeTarget.getKey();
            myPrefix = "Bạn [Chat riêng]";
        }

        if (sent) {
            Message msg = Message.voiceNote(myPrefix, fileName, duration, true);
            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            }
        } else {
            chatHistoryPanel.appendSystem("Chưa thể gửi tin nhắn thoại! Hãy kiểm tra kết nối với thiết bị đối phương.", true);
        }
    }

    private void handlePlayVoiceNote(String fileName) {
        File dir = new File(VOICE_DIR);
        File audioFile = new File(dir, fileName);
        if (!audioFile.exists()) {
            chatHistoryPanel.appendSystem("Không tìm thấy tệp ghi âm: " + fileName, true);
            return;
        }

        if (voicePlayer.isPlaying() && fileName.equals(currentlyPlayingAudio)) {
            voicePlayer.stop();
            currentlyPlayingAudio = null;
            chatHistoryPanel.appendSystem("Đã dừng phát âm thanh: " + fileName, false);
            return;
        }

        currentlyPlayingAudio = fileName;
        chatHistoryPanel.appendSystem("▶ Đang phát: " + fileName + "...", false);
        voicePlayer.play(audioFile, () -> {
            SwingUtilities.invokeLater(() -> {
                if (fileName.equals(currentlyPlayingAudio)) {
                    currentlyPlayingAudio = null;
                }
            });
        });
    }

    // ==========================================
    // Phase 5: Room Actions & Dialogs
    // ==========================================

    private void showCreateRoomDialog() {
        CreateRoomDialog dialog = new CreateRoomDialog(this, connectionManager.getNickname(), peersPanel.getAvailablePeers());
        dialog.setVisible(true);
        ChatRoom newRoom = dialog.getCreatedRoom();
        if (newRoom != null) {
            chatStorageManager.saveOrUpdateRoom(newRoom);
            peersPanel.addOrUpdateRoom(newRoom);
            connectionManager.broadcastRoomInvite(newRoom);
            switchChatTarget(ChatTarget.forRoom(newRoom));

            Message sysMsg = Message.systemInfo("🎉 Bạn đã tạo phòng riêng '" + newRoom.getName()
                    + "' với các thành viên: " + newRoom.getMembersSummary());
            String channelKey = "ROOM_" + newRoom.getId();
            conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>()).add(sysMsg);
            chatStorageManager.appendMessage(channelKey, sysMsg);
            chatHistoryPanel.appendMessage(sysMsg);
        }
    }

    private void showRenameRoomDialog() {
        ChatRoom room = getActiveRoom();
        if (room == null) return;
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới cho nhóm:", room.getName());
        if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(room.getName())) {
            String oldName = room.getName();
            room.setName(newName.trim());
            chatStorageManager.saveOrUpdateRoom(room);
            peersPanel.addOrUpdateRoom(room);
            connectionManager.broadcastRoomRename(room);
            updateChatTargetBanner();

            Message sysMsg = Message.systemInfo("Bạn đã đổi tên nhóm '" + oldName + "' thành '" + room.getName() + "'");
            String channelKey = getActiveChannelKey();
            conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>()).add(sysMsg);
            chatStorageManager.appendMessage(channelKey, sysMsg);
            chatHistoryPanel.appendMessage(sysMsg);
        }
    }

    private void showManageMembersDialog() {
        ChatRoom room = getActiveRoom();
        if (room == null) return;
        ManageMembersDialog dialog = new ManageMembersDialog(this, room, connectionManager.getNickname(), peersPanel.getAvailablePeers());
        dialog.setVisible(true);
        if (dialog.isMembersChanged()) {
            chatStorageManager.saveOrUpdateRoom(room);
            peersPanel.addOrUpdateRoom(room);
            connectionManager.broadcastRoomMemberUpdate(room);
            connectionManager.broadcastRoomInvite(room);
            updateChatTargetBanner();

            Message sysMsg = Message.systemInfo("Đã cập nhật danh sách thành viên: " + room.getMembersSummary());
            String channelKey = getActiveChannelKey();
            conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>()).add(sysMsg);
            chatStorageManager.appendMessage(channelKey, sysMsg);
            chatHistoryPanel.appendMessage(sysMsg);
        }
    }

    // ==========================================
    // Phase 5: Room Event Callbacks
    // ==========================================

    @Override
    public void onRoomInviteReceived(ChatRoom room, String invitedBy) {
        SwingUtilities.invokeLater(() -> {
            chatStorageManager.saveOrUpdateRoom(room);
            peersPanel.addOrUpdateRoom(room);

            String channelKey = "ROOM_" + room.getId();
            Message sysMsg = Message.systemInfo("👋 " + invitedBy + " đã thêm bạn vào nhóm '" + room.getName() + "'");
            conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey))).add(sysMsg);
            chatStorageManager.appendMessage(channelKey, sysMsg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(sysMsg);
            } else {
                room.incrementUnread();
                peersPanel.refreshList();
            }
        });
    }

    @Override
    public void onRoomRenamed(String roomId, String newRoomName, String updatedBy) {
        SwingUtilities.invokeLater(() -> {
            ChatRoom targetRoom = null;
            for (ChatRoom r : chatStorageManager.loadRooms()) {
                if (r.getId().equals(roomId)) {
                    targetRoom = r;
                    break;
                }
            }
            if (targetRoom != null) {
                String oldName = targetRoom.getName();
                targetRoom.setName(newRoomName);
                chatStorageManager.saveOrUpdateRoom(targetRoom);
                peersPanel.addOrUpdateRoom(targetRoom);

                String channelKey = "ROOM_" + roomId;
                Message sysMsg = Message.systemInfo("✏️ " + updatedBy + " đã đổi tên nhóm '" + oldName + "' thành '" + newRoomName + "'");
                conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey))).add(sysMsg);
                chatStorageManager.appendMessage(channelKey, sysMsg);

                if (getActiveChannelKey().equals(channelKey)) {
                    updateChatTargetBanner();
                    chatHistoryPanel.appendMessage(sysMsg);
                }
            }
        });
    }

    @Override
    public void onRoomMembersUpdated(String roomId, List<String> newMembers, String updatedBy) {
        SwingUtilities.invokeLater(() -> {
            ChatRoom targetRoom = null;
            for (ChatRoom r : chatStorageManager.loadRooms()) {
                if (r.getId().equals(roomId)) {
                    targetRoom = r;
                    break;
                }
            }
            if (targetRoom != null) {
                targetRoom.setMembers(newMembers);
                chatStorageManager.saveOrUpdateRoom(targetRoom);
                peersPanel.addOrUpdateRoom(targetRoom);

                String channelKey = "ROOM_" + roomId;
                Message sysMsg = Message.systemInfo("👥 " + updatedBy + " đã cập nhật thành viên nhóm: " + targetRoom.getMembersSummary());
                conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey))).add(sysMsg);
                chatStorageManager.appendMessage(channelKey, sysMsg);

                if (getActiveChannelKey().equals(channelKey)) {
                    updateChatTargetBanner();
                    chatHistoryPanel.appendMessage(sysMsg);
                }
            }
        });
    }

    @Override
    public void onRoomMessageReceived(String roomId, String senderNickname, String text, boolean isEncrypted) {
        SwingUtilities.invokeLater(() -> {
            String channelKey = "ROOM_" + roomId;
            Message msg = Message.userText(senderNickname, text, false, isEncrypted);

            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            } else {
                for (ChatRoom r : chatStorageManager.loadRooms()) {
                    if (r.getId().equals(roomId)) {
                        r.incrementUnread();
                        peersPanel.addOrUpdateRoom(r);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onRoomVoiceNoteReceived(String roomId, String senderNickname, String fileName, int durationSeconds, byte[] audioData) {
        SwingUtilities.invokeLater(() -> {
            try {
                File dir = new File(VOICE_DIR);
                if (!dir.exists()) dir.mkdirs();
                File targetFile = new File(dir, fileName);
                Files.write(targetFile.toPath(), audioData);
            } catch (Exception e) {
                System.err.println("Lỗi lưu file voice note nhận được: " + e.getMessage());
            }

            String channelKey = "ROOM_" + roomId;
            Message msg = Message.voiceNote(senderNickname, fileName, durationSeconds, false);

            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            } else {
                for (ChatRoom r : chatStorageManager.loadRooms()) {
                    if (r.getId().equals(roomId)) {
                        r.incrementUnread();
                        peersPanel.addOrUpdateRoom(r);
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onVoiceNoteReceived(Peer peer, String fileName, int durationSeconds, byte[] audioData, boolean isGroup) {
        SwingUtilities.invokeLater(() -> {
            // Save received WAV bytes to local disk in voice_notes/
            try {
                File dir = new File(VOICE_DIR);
                if (!dir.exists()) dir.mkdirs();
                File targetFile = new File(dir, fileName);
                Files.write(targetFile.toPath(), audioData);
            } catch (Exception e) {
                System.err.println("Lỗi lưu file voice note nhận được: " + e.getMessage());
            }

            String channelKey;
            Message msg;

            if (isGroup) {
                channelKey = GROUP_CHANNEL;
                msg = Message.voiceNote(peer.getNickname() + " [Chung]", fileName, durationSeconds, false);
            } else {
                channelKey = "PEER_" + ChatStorageManager.sanitizeFileName(peer.getNickname());
                msg = Message.voiceNote(peer.getNickname() + " [Chat riêng]", fileName, durationSeconds, false);
            }

            List<Message> history = conversationHistories.computeIfAbsent(channelKey, k -> new CopyOnWriteArrayList<>(chatStorageManager.loadMessages(channelKey)));
            history.add(msg);
            chatStorageManager.appendMessage(channelKey, msg);

            if (getActiveChannelKey().equals(channelKey)) {
                chatHistoryPanel.appendMessage(msg);
            } else {
                if (!isGroup) {
                    peer.incrementUnread();
                    peersPanel.refreshList();
                }
            }
        });
    }
}
