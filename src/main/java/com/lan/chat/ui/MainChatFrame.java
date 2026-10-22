package com.lan.chat.ui;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Message;
import com.lan.chat.model.Peer;
import com.lan.chat.network.ConnectionListener;
import com.lan.chat.network.ConnectionManager;
import com.lan.chat.network.PeerConnection;
import com.lan.chat.ui.components.ChatHistoryPanel;
import com.lan.chat.ui.components.FileTransferBar;
import com.lan.chat.ui.components.OnlinePeersPanel;
import com.lan.chat.util.AudioNotifier;
import com.lan.chat.util.NetworkUtil;
import com.lan.chat.voice.VoiceCallService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;

public class MainChatFrame extends JFrame implements ConnectionListener {

    private final ConnectionManager connectionManager;
    private Peer activeTargetPeer = null; // null = Broadcast to all (Group Chat)

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

    // Voice Call Controls
    private JButton btnCall;
    private JButton btnHangup;
    private JButton btnMute;
    private JLabel lblCallBanner;

    // Input Controls
    private JTextField txtMessageInput;
    private JButton btnSend;
    private JButton btnSelectFile;

    public MainChatFrame(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.connectionManager.addListener(this);

        initWindow();
        initHeaderPanel();
        initCenterPanel();
        initInputPanel();
        initVoiceCallbacks();

        // Default startup: Automatically launch local server & UDP Auto-Discovery
        startLocalServer();
    }

    private void initWindow() {
        setTitle("LAN Chat Pro - Chat Nhóm & Voice UDP Đa Thiết Bị");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1050, 720);
        setMinimumSize(new Dimension(880, 580));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        getContentPane().setBackground(new Color(49, 51, 56));
    }

    private void initHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(new Color(30, 31, 35));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(40, 42, 46)),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));

        // Left of Header: Local Machine Info & Server Control
        JPanel localInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        localInfoPanel.setOpaque(false);

        JLabel logo = new JLabel("⚡ LAN CHAT PRO");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(new Color(88, 101, 242));

        String myIp = NetworkUtil.getPrimaryLocalIP();
        lblMyIp = new JLabel("IP: " + myIp);
        lblMyIp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblMyIp.setForeground(new Color(200, 200, 200));

        JLabel lblPort = new JLabel("Port:");
        lblPort.setForeground(new Color(170, 170, 170));
        txtLocalPort = new JTextField(String.valueOf(connectionManager.getListenPort()), 4);
        txtLocalPort.setHorizontalAlignment(JTextField.CENTER);

        btnToggleServer = new JButton("Khởi động");
        btnToggleServer.setFocusPainted(false);
        btnToggleServer.addActionListener(e -> toggleServer());

        localInfoPanel.add(logo);
        localInfoPanel.add(Box.createHorizontalStrut(8));
        localInfoPanel.add(lblMyIp);
        localInfoPanel.add(lblPort);
        localInfoPanel.add(txtLocalPort);
        localInfoPanel.add(btnToggleServer);

        // Right of Header: Manual TCP Connection, E2EE, and Discovery Badge
        JPanel remoteConnectPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        remoteConnectPanel.setOpaque(false);

        JLabel lblRemote = new JLabel("Kết nối IP:");
        lblRemote.setForeground(new Color(170, 170, 170));

        txtTargetIp = new JTextField(myIp, 10);
        txtTargetPort = new JTextField("5000", 4);
        txtTargetPort.setHorizontalAlignment(JTextField.CENTER);

        btnConnect = new JButton("🔗 Nối TCP");
        btnConnect.setBackground(new Color(88, 101, 242));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> connectToTarget());

        chkE2ee = new JCheckBox("🔒 AES E2EE", true);
        chkE2ee.setOpaque(false);
        chkE2ee.setForeground(new Color(87, 242, 135));
        chkE2ee.setToolTipText("Mã hóa tin nhắn End-to-End bằng AES-GCM");
        chkE2ee.addActionListener(e -> connectionManager.setE2eeEnabled(chkE2ee.isSelected()));

        remoteConnectPanel.add(lblRemote);
        remoteConnectPanel.add(txtTargetIp);
        remoteConnectPanel.add(txtTargetPort);
        remoteConnectPanel.add(btnConnect);
        remoteConnectPanel.add(Box.createHorizontalStrut(6));
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
        peersPanel.setOnChatTargetSelected(peer -> {
            this.activeTargetPeer = peer;
            updateChatTargetBanner();
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
        subHeaderPanel.setBackground(new Color(38, 40, 44));
        subHeaderPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50, 53, 58)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        lblActivePeerStatus = new JLabel("📢 PHÒNG CHAT CHUNG - Gửi tin tới tất cả thiết bị đã kết nối");
        lblActivePeerStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblActivePeerStatus.setForeground(new Color(230, 230, 230));

        // Voice call action buttons
        JPanel voiceActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        voiceActions.setOpaque(false);

        lblCallBanner = new JLabel("");
        lblCallBanner.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblCallBanner.setForeground(new Color(87, 242, 135));
        lblCallBanner.setVisible(false);

        btnCall = new JButton("📞 Gọi thoại");
        btnCall.setToolTipText("Gọi thoại trực tiếp qua UDP tới đối phương");
        btnCall.setBackground(new Color(59, 165, 93));
        btnCall.setForeground(Color.WHITE);
        btnCall.setFocusPainted(false);
        btnCall.addActionListener(e -> initiateVoiceCall());

        btnHangup = new JButton("🔴 Ngắt máy");
        btnHangup.setBackground(new Color(237, 66, 69));
        btnHangup.setForeground(Color.WHITE);
        btnHangup.setFocusPainted(false);
        btnHangup.setVisible(false);
        btnHangup.addActionListener(e -> connectionManager.hangupCall());

        btnMute = new JButton("🎙️ Mic: BẬT");
        btnMute.setFocusPainted(false);
        btnMute.setVisible(false);
        btnMute.addActionListener(e -> toggleMute());

        voiceActions.add(lblCallBanner);
        voiceActions.add(btnCall);
        voiceActions.add(btnMute);
        voiceActions.add(btnHangup);

        subHeaderPanel.add(lblActivePeerStatus, BorderLayout.WEST);
        subHeaderPanel.add(voiceActions, BorderLayout.EAST);

        chatHistoryPanel = new ChatHistoryPanel();

        // Upgraded FileTransferBar with Resume and Pause actions
        fileTransferBar = new FileTransferBar();
        fileTransferBar.setOnPauseResumeAction(task -> {
            PeerConnection conn = connectionManager.getPeerConnectionByPeer(activeTargetPeer);
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
        bottomContainer.setBackground(new Color(38, 40, 44));
        bottomContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(50, 53, 58)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // Emoji Toolbar
        JPanel emojiToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        emojiToolbar.setOpaque(false);
        JLabel lblEmoji = new JLabel("Biểu tượng: ");
        lblEmoji.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblEmoji.setForeground(new Color(150, 150, 150));
        emojiToolbar.add(lblEmoji);

        String[] emojis = {"😊", "👍", "❤️", "🔥", "🎉", "🚀", "👏", "😂", "💻", "✅", "⚠️", "📁"};
        for (String emo : emojis) {
            JButton btnEmo = new JButton(emo);
            btnEmo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            btnEmo.setMargin(new Insets(1, 4, 1, 4));
            btnEmo.setFocusPainted(false);
            btnEmo.setContentAreaFilled(false);
            btnEmo.setBorderPainted(false);
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

        btnSelectFile = new JButton("📁 Chọn File");
        btnSelectFile.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSelectFile.setFocusPainted(false);
        btnSelectFile.setBackground(new Color(60, 63, 69));
        btnSelectFile.setForeground(Color.WHITE);
        btnSelectFile.addActionListener(e -> chooseAndSendFile());

        txtMessageInput = new JTextField();
        txtMessageInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtMessageInput.setBackground(new Color(56, 58, 64));
        txtMessageInput.setForeground(Color.WHITE);
        txtMessageInput.setCaretColor(Color.WHITE);
        txtMessageInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 72, 78), 1, true),
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

        btnSend = new JButton("Gửi ➤");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSend.setBackground(new Color(88, 101, 242));
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.addActionListener(e -> sendMessage());

        inputRow.add(btnSelectFile, BorderLayout.WEST);
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
                lblCallBanner.setText("📞 Đang đổ chuông...");
                lblCallBanner.setForeground(new Color(254, 231, 92));
                lblCallBanner.setVisible(true);
            }
            case RINGING -> {
                lblCallBanner.setText("🔔 Cuộc gọi đến...");
                lblCallBanner.setForeground(new Color(254, 231, 92));
                lblCallBanner.setVisible(true);
            }
            case CONNECTED -> {
                btnCall.setVisible(false);
                btnHangup.setVisible(true);
                btnMute.setVisible(true);
                lblCallBanner.setText("🟢 Đang gọi thoại (UDP)");
                lblCallBanner.setForeground(new Color(87, 242, 135));
                lblCallBanner.setVisible(true);
            }
        }
    }

    private void toggleMute() {
        boolean newMuted = !connectionManager.getVoiceCallService().isMuted();
        connectionManager.getVoiceCallService().setMuted(newMuted);
        btnMute.setText(newMuted ? "🔇 Mic: TẮT" : "🎙️ Mic: BẬT");
        btnMute.setBackground(newMuted ? new Color(237, 66, 69) : null);
    }

    private void updateChatTargetBanner() {
        if (activeTargetPeer == null) {
            lblActivePeerStatus.setText("📢 PHÒNG CHAT CHUNG - Gửi tin tới tất cả thiết bị trong mạng LAN");
            lblActivePeerStatus.setForeground(new Color(230, 230, 230));
        } else {
            lblActivePeerStatus.setText("💬 Chat Riêng với: " + activeTargetPeer.getNickname() + " (" + activeTargetPeer.getIp() + ")");
            lblActivePeerStatus.setForeground(new Color(88, 101, 242));
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
                btnToggleServer.setBackground(new Color(59, 165, 93));
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
        String prefix;

        if (activeTargetPeer == null) {
            // Group Chat: Broadcast to all connected peers
            sent = connectionManager.broadcastTextMessage(text);
            prefix = "[Chung] Bạn";
        } else {
            // Private Chat: Send to specific target peer
            sent = connectionManager.sendPrivateMessage(activeTargetPeer, text);
            prefix = "[Riêng tới " + activeTargetPeer.getNickname() + "] Bạn";
        }

        if (sent) {
            Message msg = Message.userText(prefix, text, true, connectionManager.isE2eeEnabled());
            chatHistoryPanel.appendMessage(msg);
            txtMessageInput.setText("");
        } else {
            chatHistoryPanel.appendSystem("Chưa thể gửi tin nhắn! Hãy đảm bảo đã kết nối tới ít nhất 1 thiết bị.", true);
        }
    }

    private void chooseAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file để gửi qua mạng LAN (Hỗ trợ GB & Resume)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file != null && file.exists()) {
                FileTransferTask task = connectionManager.sendFileToPeer(activeTargetPeer, file);
                if (task != null) {
                    fileTransferBar.updateTask(task);
                    chatHistoryPanel.appendSystem("Đã gửi lời mời truyền file: " + file.getName()
                            + " (" + NetworkUtil.formatBytes(file.length()) + ")", false);
                }
            }
        }
    }

    private void initiateVoiceCall() {
        Peer target = activeTargetPeer;
        if (target == null) {
            // Pick first connected peer if in Group mode
            if (!connectionManager.getConnectedPeers().isEmpty()) {
                target = connectionManager.getConnectedPeers().iterator().next().getPeer();
            }
        }

        if (target == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 thiết bị đã kết nối bên trái để bắt đầu cuộc gọi thoại!",
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
                "📞 " + peer.getNickname() + " (" + peer.getIp() + ") đang gọi thoại trực tiếp qua UDP!\nBạn có muốn nghe máy không?",
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
    public void onMessageReceived(Peer peer, String text, boolean isEncrypted) {
        SwingUtilities.invokeLater(() -> {
            Message msg = Message.userText(peer.getNickname(), text, false, isEncrypted);
            chatHistoryPanel.appendMessage(msg);
        });
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
}
