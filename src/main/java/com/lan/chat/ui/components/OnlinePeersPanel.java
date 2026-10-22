package com.lan.chat.ui.components;

import com.lan.chat.model.Peer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class OnlinePeersPanel extends JPanel {

    // Sentinel peer representing Group / All LAN chat
    public static final Peer GROUP_CHAT_PEER = new Peer("0.0.0.0", 0, "📢 PHÒNG CHAT CHUNG (Tất cả)");

    private final DefaultListModel<Peer> listModel = new DefaultListModel<>();
    private final JList<Peer> peerJList = new JList<>(listModel);
    private Consumer<Peer> onChatTargetSelected;
    private Consumer<Peer> onPeerConnectRequest;

    private final JLabel lblPeerCount = new JLabel("(0 online)");

    public OnlinePeersPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 31, 35));
        setPreferredSize(new Dimension(260, 0));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(25, 26, 30));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel titleLabel = new JLabel("🌐 MẠNG NỘI BỘ LAN");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(new Color(180, 185, 195));

        lblPeerCount.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPeerCount.setForeground(new Color(120, 125, 135));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(lblPeerCount, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Always put Group Chat as top item
        listModel.addElement(GROUP_CHAT_PEER);

        // List Configuration
        peerJList.setBackground(new Color(30, 31, 35));
        peerJList.setForeground(Color.WHITE);
        peerJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerJList.setCellRenderer(new PeerCellRenderer());
        peerJList.setSelectedIndex(0);

        peerJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Peer selected = peerJList.getSelectedValue();
                if (selected != null && onChatTargetSelected != null) {
                    onChatTargetSelected.accept(selected.equals(GROUP_CHAT_PEER) ? null : selected);
                }
            }
        });

        peerJList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Peer selected = peerJList.getSelectedValue();
                    if (selected != null && !selected.equals(GROUP_CHAT_PEER) && onPeerConnectRequest != null) {
                        onPeerConnectRequest.accept(selected);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(peerJList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(30, 31, 35));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom hint
        JPanel bottomHint = new JPanel(new GridLayout(2, 1, 0, 2));
        bottomHint.setBackground(new Color(25, 26, 30));
        bottomHint.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        JLabel hint1 = new JLabel("📡 Tự động dò tìm qua UDP Broadcast", SwingConstants.CENTER);
        hint1.setFont(new Font("Segoe UI", Font.ITALIC, 10));
        hint1.setForeground(new Color(87, 242, 135));

        JLabel hint2 = new JLabel("💡 Chọn kênh chung hoặc người cụ thể", SwingConstants.CENTER);
        hint2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hint2.setForeground(new Color(140, 145, 155));

        bottomHint.add(hint1);
        bottomHint.add(hint2);
        add(bottomHint, BorderLayout.SOUTH);
    }

    public void setOnChatTargetSelected(Consumer<Peer> onChatTargetSelected) {
        this.onChatTargetSelected = onChatTargetSelected;
    }

    public void setOnPeerConnectRequest(Consumer<Peer> onPeerConnectRequest) {
        this.onPeerConnectRequest = onPeerConnectRequest;
    }

    public Peer getSelectedTarget() {
        Peer selected = peerJList.getSelectedValue();
        return (selected == null || selected.equals(GROUP_CHAT_PEER)) ? null : selected;
    }

    public void addOrUpdatePeer(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            int index = -1;
            for (int i = 1; i < listModel.size(); i++) { // Skip group chat item at 0
                Peer p = listModel.get(i);
                if (p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort()) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                listModel.set(index, peer);
            } else {
                listModel.addElement(peer);
            }
            updateCountLabel();
        });
    }

    public void removePeer(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 1; i < listModel.size(); i++) {
                Peer p = listModel.get(i);
                if (p.getIp().equals(peer.getIp()) && p.getPort() == peer.getPort()) {
                    listModel.remove(i);
                    break;
                }
            }
            updateCountLabel();
        });
    }

    private void updateCountLabel() {
        int count = Math.max(0, listModel.size() - 1);
        lblPeerCount.setText("(" + count + " thiết bị)");
    }

    private static class PeerCellRenderer extends JPanel implements ListCellRenderer<Peer> {
        private final JLabel lblAvatar = new JLabel();
        private final JLabel lblName = new JLabel();
        private final JLabel lblIp = new JLabel();
        private final JLabel lblStatus = new JLabel();

        public PeerCellRenderer() {
            setLayout(new BorderLayout(8, 2));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

            lblAvatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 16));

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblName.setForeground(new Color(230, 230, 230));

            lblIp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblIp.setForeground(new Color(160, 160, 160));

            textPanel.add(lblName);
            textPanel.add(lblIp);

            lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            add(lblAvatar, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(lblStatus, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Peer> list, Peer peer, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (peer.equals(GROUP_CHAT_PEER)) {
                lblAvatar.setText("📢");
                lblName.setText("Phòng Chat Chung");
                lblName.setForeground(new Color(88, 101, 242));
                lblIp.setText("Gửi tin tới mọi máy trong LAN");
                lblStatus.setText("Tất cả");
                lblStatus.setForeground(new Color(235, 69, 158));
            } else {
                lblAvatar.setText("💻");
                lblName.setText(peer.getNickname());
                lblName.setForeground(new Color(230, 230, 230));
                lblIp.setText(peer.getIp() + ":" + peer.getPort());

                if (peer.isConnected()) {
                    lblStatus.setText("🔵 Đã nối");
                    lblStatus.setForeground(new Color(88, 101, 242));
                } else if (peer.isOnline()) {
                    lblStatus.setText("🟢 Online");
                    lblStatus.setForeground(new Color(59, 165, 93));
                } else {
                    lblStatus.setText("⚪ Offline");
                    lblStatus.setForeground(new Color(120, 120, 120));
                }
            }

            if (isSelected) {
                setBackground(new Color(53, 56, 62));
            } else {
                setBackground(new Color(30, 31, 35));
            }
            return this;
        }
    }
}
