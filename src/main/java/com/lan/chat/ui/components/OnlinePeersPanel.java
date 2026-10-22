package com.lan.chat.ui.components;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.ChatTarget;
import com.lan.chat.model.Peer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OnlinePeersPanel extends JPanel {

    private final DefaultListModel<ChatTarget> listModel = new DefaultListModel<>();
    private final JList<ChatTarget> targetJList = new JList<>(listModel);
    private Consumer<ChatTarget> onTargetSelected;
    private Consumer<Peer> onPeerConnectRequest;
    private Runnable onCreateRoomRequested;
    private Runnable onRescanRequested;
    private Consumer<ChatRoom> onLeaveRoomRequested;

    private final JLabel lblPeerCount = new JLabel("(0 online)");

    public OnlinePeersPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        setPreferredSize(new Dimension(280, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftHeader.setOpaque(false);

        JLabel titleLabel = new JLabel("HỘI THOẠI");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(new Color(15, 23, 42));

        lblPeerCount.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPeerCount.setForeground(new Color(100, 116, 139));

        leftHeader.add(titleLabel);
        leftHeader.add(lblPeerCount);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightHeader.setOpaque(false);

        JButton btnRescan = new JButton("Quét lại");
        btnRescan.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRescan.setBackground(new Color(241, 245, 249));
        btnRescan.setForeground(new Color(71, 85, 105));
        btnRescan.setFocusPainted(false);
        btnRescan.setToolTipText("Quét tìm lại các thiết bị online trong mạng LAN");
        btnRescan.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        btnRescan.addActionListener(e -> {
            if (onRescanRequested != null) {
                onRescanRequested.run();
            }
        });

        JButton btnCreateRoom = new JButton("+ Nhóm");
        btnCreateRoom.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCreateRoom.setBackground(new Color(79, 70, 229));
        btnCreateRoom.setForeground(Color.WHITE);
        btnCreateRoom.setFocusPainted(false);
        btnCreateRoom.setToolTipText("Tạo phòng chat riêng và chọn các thành viên tham gia");
        btnCreateRoom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        btnCreateRoom.addActionListener(e -> {
            if (onCreateRoomRequested != null) {
                onCreateRoomRequested.run();
            }
        });

        rightHeader.add(btnRescan);
        rightHeader.add(btnCreateRoom);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(rightHeader, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Always put General Room (All LAN) as top item
        listModel.addElement(ChatTarget.allLan());

        // List Configuration
        targetJList.setBackground(new Color(248, 250, 252));
        targetJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        targetJList.setCellRenderer(new TargetCellRenderer());
        targetJList.setSelectedIndex(0);

        targetJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ChatTarget selected = targetJList.getSelectedValue();
                if (selected != null && onTargetSelected != null) {
                    onTargetSelected.accept(selected);
                }
            }
        });

        JPopupMenu roomPopupMenu = new JPopupMenu();
        JMenuItem itemLeaveRoom = new JMenuItem("🚪 Thoát nhóm");
        itemLeaveRoom.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        itemLeaveRoom.setForeground(new Color(220, 38, 38));
        itemLeaveRoom.addActionListener(e -> {
            ChatTarget selected = targetJList.getSelectedValue();
            if (selected != null && selected.isRoom() && onLeaveRoomRequested != null) {
                onLeaveRoomRequested.accept(selected.getRoom());
            }
        });
        roomPopupMenu.add(itemLeaveRoom);

        targetJList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    ChatTarget selected = targetJList.getSelectedValue();
                    if (selected != null && selected.isPeer() && onPeerConnectRequest != null) {
                        onPeerConnectRequest.accept(selected.getPeer());
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handleContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleContextMenu(e);
            }

            private void handleContextMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = targetJList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        targetJList.setSelectedIndex(index);
                        ChatTarget selected = listModel.get(index);
                        if (selected != null && selected.isRoom()) {
                            roomPopupMenu.show(targetJList, e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(targetJList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(248, 250, 252));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom hints
        JPanel bottomHint = new JPanel(new GridLayout(2, 1, 0, 3));
        bottomHint.setBackground(Color.WHITE);
        bottomHint.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)
        ));

        JLabel hint1 = new JLabel("● Tự động dò tìm qua UDP Broadcast", SwingConstants.CENTER);
        hint1.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint1.setForeground(new Color(5, 150, 105));

        JLabel hint2 = new JLabel("Hỗ trợ Phòng nhóm & Chat 1-1", SwingConstants.CENTER);
        hint2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint2.setForeground(new Color(100, 116, 139));

        bottomHint.add(hint1);
        bottomHint.add(hint2);
        add(bottomHint, BorderLayout.SOUTH);
    }

    public void setOnTargetSelected(Consumer<ChatTarget> onTargetSelected) {
        this.onTargetSelected = onTargetSelected;
    }

    public void setOnPeerConnectRequest(Consumer<Peer> onPeerConnectRequest) {
        this.onPeerConnectRequest = onPeerConnectRequest;
    }

    public void setOnCreateRoomRequested(Runnable onCreateRoomRequested) {
        this.onCreateRoomRequested = onCreateRoomRequested;
    }

    public void setOnRescanRequested(Runnable onRescanRequested) {
        this.onRescanRequested = onRescanRequested;
    }

    public void setOnLeaveRoomRequested(Consumer<ChatRoom> onLeaveRoomRequested) {
        this.onLeaveRoomRequested = onLeaveRoomRequested;
    }

    public ChatTarget getSelectedTarget() {
        return targetJList.getSelectedValue();
    }

    public void selectTarget(ChatTarget target) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).equals(target)) {
                    targetJList.setSelectedIndex(i);
                    targetJList.ensureIndexIsVisible(i);
                    break;
                }
            }
        });
    }

    public void selectRoom(ChatRoom room) {
        if (room == null) return;
        selectTarget(ChatTarget.forRoom(room));
    }

    public void addOrUpdateRoom(ChatRoom room) {
        SwingUtilities.invokeLater(() -> {
            int foundIndex = -1;
            int firstPeerIndex = listModel.size();

            for (int i = 1; i < listModel.size(); i++) {
                ChatTarget t = listModel.get(i);
                if (t.isRoom() && t.getRoom().getId().equals(room.getId())) {
                    foundIndex = i;
                    break;
                }
                if (t.isPeer() && firstPeerIndex == listModel.size()) {
                    firstPeerIndex = i;
                }
            }

            ChatTarget newTarget = ChatTarget.forRoom(room);
            if (foundIndex >= 0) {
                listModel.set(foundIndex, newTarget);
            } else {
                // Insert room before peers
                listModel.add(firstPeerIndex, newTarget);
            }
            refreshList();
        });
    }

    public void removeRoom(String roomId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 1; i < listModel.size(); i++) {
                ChatTarget t = listModel.get(i);
                if (t.isRoom() && t.getRoom().getId().equals(roomId)) {
                    listModel.remove(i);
                    break;
                }
            }
            refreshList();
        });
    }

    public void addOrUpdatePeer(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            int foundIndex = -1;
            for (int i = 1; i < listModel.size(); i++) {
                ChatTarget t = listModel.get(i);
                if (t.isPeer()) {
                    Peer p = t.getPeer();
                    if ((p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort())
                            || (p.getNickname().equalsIgnoreCase(peer.getNickname()))) {
                        foundIndex = i;
                        break;
                    }
                }
            }

            ChatTarget target = ChatTarget.forPeer(peer);
            if (foundIndex >= 0) {
                listModel.set(foundIndex, target);
            } else {
                listModel.addElement(target);
            }
            updateCountLabel();
            refreshList();
        });
    }

    public void removePeer(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 1; i < listModel.size(); i++) {
                ChatTarget t = listModel.get(i);
                if (t.isPeer()) {
                    Peer p = t.getPeer();
                    if ((p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort())
                            || (p.getNickname().equalsIgnoreCase(peer.getNickname()))) {
                        listModel.remove(i);
                        break;
                    }
                }
            }
            updateCountLabel();
            refreshList();
        });
    }

    public List<Peer> getAvailablePeers() {
        List<Peer> peers = new ArrayList<>();
        for (int i = 1; i < listModel.size(); i++) {
            ChatTarget t = listModel.get(i);
            if (t.isPeer() && t.getPeer() != null) {
                peers.add(t.getPeer());
            }
        }
        return peers;
    }

    private void updateCountLabel() {
        int onlineCount = 0;
        for (int i = 1; i < listModel.size(); i++) {
            ChatTarget t = listModel.get(i);
            if (t.isPeer()) {
                Peer p = t.getPeer();
                if (p.isConnected() || p.isOnline()) {
                    onlineCount++;
                }
            }
        }
        lblPeerCount.setText("(" + onlineCount + " online)");
    }

    public void refreshList() {
        SwingUtilities.invokeLater(targetJList::repaint);
    }

    private static class TargetCellRenderer extends JPanel implements ListCellRenderer<ChatTarget> {
        private final JLabel lblBadge = new JLabel();
        private final JLabel lblName = new JLabel();
        private final JLabel lblDesc = new JLabel();
        private final JLabel lblStatus = new JLabel();

        public TargetCellRenderer() {
            setLayout(new BorderLayout(8, 2));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(3, 6, 3, 6),
                    BorderFactory.createEmptyBorder(5, 6, 5, 6)
            ));

            lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            lblBadge.setHorizontalAlignment(SwingConstants.CENTER);
            lblBadge.setPreferredSize(new Dimension(42, 32));
            lblBadge.setOpaque(true);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblDesc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));

            textPanel.add(lblName);
            textPanel.add(lblDesc);

            add(lblBadge, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(lblStatus, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatTarget> list, ChatTarget target,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            if (target.isAllLan()) {
                lblBadge.setText("ALL");
                lblBadge.setBackground(new Color(224, 231, 255));
                lblBadge.setForeground(new Color(67, 56, 202));

                lblName.setText("Phòng Chat Chung");
                lblName.setForeground(new Color(67, 56, 202));

                lblDesc.setText("Gửi tin tới mọi máy LAN");
                lblDesc.setForeground(new Color(100, 116, 139));

                lblStatus.setText("● Tất cả");
                lblStatus.setForeground(new Color(79, 70, 229));

                setBackground(isSelected ? new Color(224, 231, 255) : new Color(238, 242, 255));

            } else if (target.isRoom()) {
                ChatRoom room = target.getRoom();
                lblBadge.setText("NHÓM");
                lblBadge.setBackground(new Color(209, 250, 229));
                lblBadge.setForeground(new Color(5, 150, 105));

                lblName.setText(room.getName());
                lblName.setForeground(new Color(15, 23, 42));

                lblDesc.setText(room.getMembers().size() + " TV: " + room.getMembersSummary());
                lblDesc.setForeground(new Color(100, 116, 139));

                if (room.getUnreadCount() > 0) {
                    lblStatus.setText("● " + room.getUnreadCount() + " tin mới");
                    lblStatus.setForeground(new Color(220, 38, 38));
                } else {
                    lblStatus.setText("👥 Nhóm");
                    lblStatus.setForeground(new Color(16, 185, 129));
                }

                setBackground(isSelected ? new Color(224, 231, 255) : Color.WHITE);

            } else {
                Peer peer = target.getPeer();
                lblBadge.setText("LAN");
                lblBadge.setBackground(new Color(241, 245, 249));
                lblBadge.setForeground(new Color(71, 85, 105));

                lblName.setText(peer.getNickname());
                lblName.setForeground(new Color(15, 23, 42));

                lblDesc.setText(peer.getIp() + ":" + peer.getPort());
                lblDesc.setForeground(new Color(100, 116, 139));

                if (peer.getUnreadCount() > 0) {
                    lblStatus.setText("● " + peer.getUnreadCount() + " tin mới");
                    lblStatus.setForeground(new Color(220, 38, 38));
                } else if (peer.isConnected()) {
                    lblStatus.setText("● Đã nối");
                    lblStatus.setForeground(new Color(37, 99, 235));
                } else if (peer.isOnline()) {
                    lblStatus.setText("● Online");
                    lblStatus.setForeground(new Color(16, 185, 129));
                } else {
                    lblStatus.setText("○ Offline");
                    lblStatus.setForeground(new Color(148, 163, 184));
                }

                setBackground(isSelected ? new Color(224, 231, 255) : Color.WHITE);
            }

            return this;
        }
    }
}
