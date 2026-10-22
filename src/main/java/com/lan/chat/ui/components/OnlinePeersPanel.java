package com.lan.chat.ui.components;

import com.lan.chat.model.Peer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class OnlinePeersPanel extends JPanel {

    // Sentinel peer representing Group / All LAN chat
    public static final Peer GROUP_CHAT_PEER = new Peer("0.0.0.0", 0, "PHÒNG CHAT CHUNG (Tất cả)");

    private final DefaultListModel<Peer> listModel = new DefaultListModel<>();
    private final JList<Peer> peerJList = new JList<>(listModel);
    private Consumer<Peer> onChatTargetSelected;
    private Consumer<Peer> onPeerConnectRequest;

    private final JLabel lblPeerCount = new JLabel("(0 online)");

    public OnlinePeersPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));
        setPreferredSize(new Dimension(260, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        JLabel titleLabel = new JLabel("MẠNG NỘI BỘ LAN");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(new Color(15, 23, 42));

        lblPeerCount.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblPeerCount.setForeground(new Color(100, 116, 139));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(lblPeerCount, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Always put Group Chat as top item
        listModel.addElement(GROUP_CHAT_PEER);

        // List Configuration
        peerJList.setBackground(new Color(248, 250, 252));
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

        JLabel hint2 = new JLabel("Nhấp đúp thiết bị để kết nối", SwingConstants.CENTER);
        hint2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint2.setForeground(new Color(100, 116, 139));

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
        private final JLabel lblBadge = new JLabel();
        private final JLabel lblName = new JLabel();
        private final JLabel lblIp = new JLabel();
        private final JLabel lblStatus = new JLabel();

        public PeerCellRenderer() {
            setLayout(new BorderLayout(10, 2));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(4, 8, 4, 8),
                    BorderFactory.createEmptyBorder(6, 8, 6, 8)
            ));

            lblBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblBadge.setHorizontalAlignment(SwingConstants.CENTER);
            lblBadge.setPreferredSize(new Dimension(36, 32));
            lblBadge.setOpaque(true);

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            textPanel.setOpaque(false);

            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblIp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 11));

            textPanel.add(lblName);
            textPanel.add(lblIp);

            add(lblBadge, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(lblStatus, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Peer> list, Peer peer, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (peer.equals(GROUP_CHAT_PEER)) {
                lblBadge.setText("ALL");
                lblBadge.setBackground(new Color(224, 231, 255));
                lblBadge.setForeground(new Color(67, 56, 202));

                lblName.setText("Phòng Chat Chung");
                lblName.setForeground(new Color(67, 56, 202));

                lblIp.setText("Gửi tin tới mọi máy LAN");
                lblIp.setForeground(new Color(100, 116, 139));

                lblStatus.setText("● Tất cả");
                lblStatus.setForeground(new Color(79, 70, 229));

                if (isSelected) {
                    setBackground(new Color(224, 231, 255));
                } else {
                    setBackground(new Color(238, 242, 255));
                }
            } else {
                lblBadge.setText("LAN");
                lblBadge.setBackground(new Color(241, 245, 249));
                lblBadge.setForeground(new Color(71, 85, 105));

                lblName.setText(peer.getNickname());
                lblName.setForeground(new Color(15, 23, 42));

                lblIp.setText(peer.getIp() + ":" + peer.getPort());
                lblIp.setForeground(new Color(100, 116, 139));

                if (peer.isConnected()) {
                    lblStatus.setText("● Đã nối");
                    lblStatus.setForeground(new Color(37, 99, 235));
                } else if (peer.isOnline()) {
                    lblStatus.setText("● Online");
                    lblStatus.setForeground(new Color(16, 185, 129));
                } else {
                    lblStatus.setText("○ Offline");
                    lblStatus.setForeground(new Color(148, 163, 184));
                }

                if (isSelected) {
                    setBackground(new Color(224, 231, 255));
                } else {
                    setBackground(Color.WHITE);
                }
            }
            return this;
        }
    }
}
