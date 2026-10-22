package com.lan.chat.ui.dialogs;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.Peer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modern modal dialog for viewing, adding, and removing members in an existing group room.
 */
public class ManageMembersDialog extends JDialog {

    private final ChatRoom room;
    private final String myNickname;
    private final List<Peer> availablePeers;
    private final List<JCheckBox> memberCheckBoxes = new ArrayList<>();
    private boolean membersChanged = false;

    public ManageMembersDialog(JFrame parent, ChatRoom room, String myNickname, List<Peer> availablePeers) {
        super(parent, "Quản lý thành viên - " + room.getName(), true);
        this.room = room;
        this.myNickname = myNickname;
        this.availablePeers = availablePeers != null ? availablePeers : new ArrayList<>();

        setSize(440, 500);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)
        ));

        JLabel lblTitle = new JLabel("Thành viên nhóm: " + room.getName());
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(new Color(67, 56, 202));

        JLabel lblDesc = new JLabel("Tích chọn để thêm vào nhóm, bỏ chọn để xóa thành viên khỏi nhóm.");
        lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblDesc.setForeground(new Color(100, 116, 139));

        headerPanel.add(lblTitle, BorderLayout.NORTH);
        headerPanel.add(lblDesc, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Center Checklist
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(248, 250, 252));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JLabel lblSub = new JLabel("Danh sách thành viên & thiết bị LAN:");
        lblSub.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblSub.setForeground(new Color(15, 23, 42));
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(lblSub);
        contentPanel.add(Box.createVerticalStrut(8));

        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(Color.WHITE);
        listContainer.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // 1. Current user (always selected and disabled from unchecking self)
        JCheckBox cbMe = new JCheckBox("Bạn (" + myNickname + ") - Quản trị viên");
        cbMe.setSelected(true);
        cbMe.setEnabled(false);
        cbMe.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cbMe.setForeground(new Color(67, 56, 202));
        listContainer.add(cbMe);
        listContainer.add(Box.createVerticalStrut(4));

        Set<String> processedNicks = new LinkedHashSet<>();
        processedNicks.add(myNickname.toLowerCase());

        // 2. Existing members from room definition
        for (String member : room.getMembers()) {
            if (member.equalsIgnoreCase(myNickname)) continue;
            processedNicks.add(member.toLowerCase());

            JCheckBox cb = new JCheckBox(member + " (Đã tham gia)");
            cb.setSelected(true);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cb.putClientProperty("nickname", member);
            memberCheckBoxes.add(cb);
            listContainer.add(cb);
            listContainer.add(Box.createVerticalStrut(4));
        }

        // 3. Available peers online / contacts not yet in room
        for (Peer p : this.availablePeers) {
            String nick = p.getNickname();
            if (nick == null || nick.trim().isEmpty() || processedNicks.contains(nick.toLowerCase())) {
                continue;
            }
            processedNicks.add(nick.toLowerCase());

            String status = p.isConnected() ? " [Đã nối]" : (p.isOnline() ? " [Online]" : " [Offline]");
            JCheckBox cb = new JCheckBox(nick + " (" + p.getIp() + status + ")");
            cb.setSelected(false);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cb.putClientProperty("nickname", nick);
            memberCheckBoxes.add(cb);
            listContainer.add(cb);
            listContainer.add(Box.createVerticalStrut(4));
        }

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(390, 280));
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

        JButton btnSave = new JButton("Lưu thay đổi");
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSave.setBackground(new Color(79, 70, 229));
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);
        btnSave.addActionListener(e -> handleSave());

        actionPanel.add(btnCancel);
        actionPanel.add(btnSave);
        add(actionPanel, BorderLayout.SOUTH);
    }

    private void handleSave() {
        List<String> newMembers = new ArrayList<>();
        newMembers.add(myNickname);

        for (JCheckBox cb : memberCheckBoxes) {
            if (cb.isSelected()) {
                String nick = (String) cb.getClientProperty("nickname");
                if (nick != null && !newMembers.contains(nick)) {
                    newMembers.add(nick);
                }
            }
        }

        room.setMembers(newMembers);
        this.membersChanged = true;
        dispose();
    }

    public boolean isMembersChanged() {
        return membersChanged;
    }
}
