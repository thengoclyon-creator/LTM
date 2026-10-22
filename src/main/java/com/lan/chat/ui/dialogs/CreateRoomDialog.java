package com.lan.chat.ui.dialogs;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.Peer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern modal dialog for creating a custom private group room
 * and selecting which LAN peers to invite as members.
 */
public class CreateRoomDialog extends JDialog {

    private final JTextField txtRoomName;
    private final JPanel peerChecklistPanel;
    private final List<JCheckBox> checkBoxes = new ArrayList<>();
    private final List<Peer> availablePeers;
    private final String myNickname;
    private ChatRoom createdRoom = null;

    public CreateRoomDialog(JFrame parent, String myNickname, List<Peer> availablePeers) {
        super(parent, "Tạo phòng riêng / Nhóm mới", true);
        this.myNickname = myNickname;
        this.availablePeers = availablePeers != null ? availablePeers : new ArrayList<>();

        setSize(420, 480);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());

        // Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)
        ));

        JLabel lblTitle = new JLabel("Tạo nhóm chat riêng mạng LAN");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(67, 56, 202));

        JLabel lblDesc = new JLabel("Nhập tên nhóm và chọn các thành viên tham gia cuộc trò chuyện.");
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblDesc.setForeground(new Color(100, 116, 139));

        headerPanel.add(lblTitle, BorderLayout.NORTH);
        headerPanel.add(lblDesc, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Center Content
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        // Group Name Input
        JLabel lblName = new JLabel("Tên nhóm:");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblName.setForeground(new Color(15, 23, 42));
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtRoomName = new JTextField();
        txtRoomName.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtRoomName.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        txtRoomName.setPreferredSize(new Dimension(380, 34));
        txtRoomName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        txtRoomName.setAlignmentX(Component.LEFT_ALIGNMENT);

        contentPanel.add(lblName);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(txtRoomName);
        contentPanel.add(Box.createVerticalStrut(14));

        // Member selection checklist
        JLabel lblSelect = new JLabel("Chọn thành viên mời vào nhóm:");
        lblSelect.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblSelect.setForeground(new Color(15, 23, 42));
        lblSelect.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(lblSelect);
        contentPanel.add(Box.createVerticalStrut(6));

        peerChecklistPanel = new JPanel();
        peerChecklistPanel.setLayout(new BoxLayout(peerChecklistPanel, BoxLayout.Y_AXIS));
        peerChecklistPanel.setBackground(Color.WHITE);
        peerChecklistPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        boolean hasPeers = false;
        for (Peer p : this.availablePeers) {
            String nick = p.getNickname();
            if (nick == null || nick.trim().isEmpty() || nick.equalsIgnoreCase(myNickname)) {
                continue;
            }
            hasPeers = true;
            String status = p.isConnected() ? " [Đã nối]" : (p.isOnline() ? " [Online]" : " [Offline]");
            JCheckBox cb = new JCheckBox(nick + " (" + p.getIp() + status + ")");
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cb.setOpaque(false);
            cb.putClientProperty("peer", p);
            checkBoxes.add(cb);
            peerChecklistPanel.add(cb);
            peerChecklistPanel.add(Box.createVerticalStrut(4));
        }

        if (!hasPeers) {
            JLabel lblEmpty = new JLabel("<html><i>Chưa phát hiện thiết bị nào khác online.<br>Bạn vẫn có thể tạo nhóm và thêm thành viên sau!</i></html>");
            lblEmpty.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblEmpty.setForeground(new Color(100, 116, 139));
            peerChecklistPanel.add(lblEmpty);
        }

        JScrollPane scrollPane = new JScrollPane(peerChecklistPanel);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(380, 200));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true));
        contentPanel.add(scrollPane);

        add(contentPanel, BorderLayout.CENTER);

        // Bottom Actions
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        actionPanel.setBackground(Color.WHITE);
        actionPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)));

        JButton btnCancel = new JButton("Hủy");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancel.setFocusPainted(false);
        btnCancel.addActionListener(e -> dispose());

        JButton btnCreate = new JButton("Tạo nhóm");
        btnCreate.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCreate.setBackground(new Color(79, 70, 229));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setFocusPainted(false);
        btnCreate.addActionListener(e -> handleCreate());

        actionPanel.add(btnCancel);
        actionPanel.add(btnCreate);
        add(actionPanel, BorderLayout.SOUTH);
    }

    private void handleCreate() {
        String name = txtRoomName.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên nhóm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            txtRoomName.requestFocus();
            return;
        }

        List<String> selectedMembers = new ArrayList<>();
        selectedMembers.add(myNickname);

        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                Peer p = (Peer) cb.getClientProperty("peer");
                if (p != null && p.getNickname() != null) {
                    selectedMembers.add(p.getNickname());
                }
            }
        }

        String roomId = "room_" + System.currentTimeMillis() + "_" + Math.abs(name.hashCode());
        this.createdRoom = new ChatRoom(roomId, name, myNickname, selectedMembers);
        dispose();
    }

    public ChatRoom getCreatedRoom() {
        return createdRoom;
    }
}
