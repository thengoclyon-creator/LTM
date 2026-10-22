package com.lan.chat.ui;

import com.lan.chat.history.ChatHistoryManager;
import com.lan.chat.model.Message;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Hộp thoại giao diện tra cứu, tìm kiếm, xuất file và quản lý toàn diện lịch sử trò chuyện.
 */
public class ChatHistoryDialog extends JDialog {

    private final ChatHistoryManager historyManager;
    private final DefaultListModel<ChatHistoryManager.ConversationInfo> conversationListModel = new DefaultListModel<>();
    private final JList<ChatHistoryManager.ConversationInfo> conversationJList = new JList<>(conversationListModel);

    private final JTextField txtSearch = new JTextField(20);
    private final JButton btnSearch = new JButton("🔍 Tìm");
    private final JButton btnClearSearch = new JButton("✕ Xóa lọc");

    private final JLabel lblCurrentTitle = new JLabel("Chọn một cuộc hội thoại bên trái");
    private final JLabel lblCurrentCount = new JLabel("");
    private final JTextPane previewPane = new JTextPane();
    private final HTMLEditorKit editorKit = new HTMLEditorKit();
    private final HTMLDocument document;

    private final JButton btnExportTxt = new JButton("📄 Xuất file TXT");
    private final JButton btnExportHtml = new JButton("🌐 Xuất file HTML");
    private final JButton btnDelete = new JButton("🗑️ Xóa lịch sử này");
    private final JButton btnClose = new JButton("Đóng");

    private String activeKey = ChatHistoryManager.GROUP_CONVERSATION_KEY;

    public ChatHistoryDialog(Frame owner, ChatHistoryManager historyManager, String initialConversationKey) {
        super(owner, "📜 Quản Lý Lịch Sử Chat - LAN Chat Pro", true);
        this.historyManager = historyManager;
        if (initialConversationKey != null && !initialConversationKey.isEmpty()) {
            this.activeKey = initialConversationKey;
        }

        setSize(920, 620);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        // Setup HTML preview pane
        previewPane.setEditable(false);
        previewPane.setContentType("text/html; charset=UTF-8");
        previewPane.setEditorKit(editorKit);
        document = (HTMLDocument) previewPane.getDocument();

        String css = """
            body {
                font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                background-color: #F8FAFC;
                color: #1E293B;
                margin: 14px;
            }
            .msg-row-me { text-align: right; margin-bottom: 8px; }
            .msg-bubble-me {
                display: inline-block;
                background-color: #4F46E5;
                color: #FFFFFF;
                padding: 8px 14px;
                border-radius: 10px;
                max-width: 75%;
                text-align: left;
                font-size: 13px;
            }
            .msg-row-peer { text-align: left; margin-bottom: 8px; }
            .msg-bubble-peer {
                display: inline-block;
                background-color: #FFFFFF;
                color: #1E293B;
                padding: 8px 14px;
                border-radius: 10px;
                border: 1px solid #E2E8F0;
                max-width: 75%;
                text-align: left;
                font-size: 13px;
            }
            .sender { font-weight: bold; font-size: 11px; color: #4F46E5; margin-bottom: 3px; }
            .meta { font-size: 10px; color: #64748B; margin-top: 3px; }
            .meta-me { color: #E0E7FF; text-align: right; }
            mark { background-color: #FEF08A; color: #854D0E; font-weight: bold; border-radius: 2px; }
            .system-bubble {
                display: inline-block;
                background-color: #F1F5F9;
                color: #475569;
                padding: 4px 10px;
                border-radius: 8px;
                font-size: 11px;
                border: 1px solid #E2E8F0;
            }
        """;
        editorKit.getStyleSheet().addRule(css);

        initTopBar();
        initCenterSplit();
        initBottomBar();

        loadConversationList();
    }

    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setBackground(Color.WHITE);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JLabel title = new JLabel("LỊCH SỬ TIN NHẮN THEO PHÒNG & THIẾT BỊ");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(15, 23, 42));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);

        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm từ khóa tin nhắn...");
        btnSearch.setBackground(new Color(79, 70, 229));
        btnSearch.setForeground(Color.WHITE);
        btnSearch.setFocusPainted(false);
        btnSearch.addActionListener(e -> executeSearch());
        txtSearch.addActionListener(e -> executeSearch());

        btnClearSearch.setFocusPainted(false);
        btnClearSearch.addActionListener(e -> {
            txtSearch.setText("");
            loadConversationList();
        });

        searchPanel.add(new JLabel("Tìm kiếm:"));
        searchPanel.add(txtSearch);
        searchPanel.add(btnSearch);
        searchPanel.add(btnClearSearch);

        topBar.add(title, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);
    }

    private void initCenterSplit() {
        // Left: Conversations List
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(248, 250, 252));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)));

        JLabel leftTitle = new JLabel("Hội thoại đã lưu", SwingConstants.LEFT);
        leftTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        leftTitle.setForeground(new Color(100, 116, 139));
        leftTitle.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        leftPanel.add(leftTitle, BorderLayout.NORTH);

        conversationJList.setBackground(new Color(248, 250, 252));
        conversationJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationJList.setCellRenderer(new ConversationRenderer());
        conversationJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ChatHistoryManager.ConversationInfo info = conversationJList.getSelectedValue();
                if (info != null) {
                    this.activeKey = info.key();
                    displayConversation(info.key(), null);
                }
            }
        });

        JScrollPane leftScroll = new JScrollPane(conversationJList);
        leftScroll.setBorder(BorderFactory.createEmptyBorder());
        leftPanel.add(leftScroll, BorderLayout.CENTER);

        // Right: Chat Transcript Review
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(Color.WHITE);

        JPanel rightHeader = new JPanel(new BorderLayout(8, 0));
        rightHeader.setBackground(Color.WHITE);
        rightHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        lblCurrentTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCurrentTitle.setForeground(new Color(15, 23, 42));

        lblCurrentCount.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblCurrentCount.setForeground(new Color(100, 116, 139));

        rightHeader.add(lblCurrentTitle, BorderLayout.WEST);
        rightHeader.add(lblCurrentCount, BorderLayout.EAST);
        rightPanel.add(rightHeader, BorderLayout.NORTH);

        JScrollPane previewScroll = new JScrollPane(previewPane);
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.getViewport().setBackground(new Color(248, 250, 252));
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);
        rightPanel.add(previewScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(280);
        splitPane.setDividerSize(4);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        add(splitPane, BorderLayout.CENTER);
    }

    private void initBottomBar() {
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBackground(Color.WHITE);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);

        btnExportTxt.setFocusPainted(false);
        btnExportTxt.addActionListener(e -> exportActiveConversation(false));

        btnExportHtml.setFocusPainted(false);
        btnExportHtml.setBackground(new Color(79, 70, 229));
        btnExportHtml.setForeground(Color.WHITE);
        btnExportHtml.addActionListener(e -> exportActiveConversation(true));

        btnDelete.setFocusPainted(false);
        btnDelete.setBackground(new Color(239, 68, 68));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.addActionListener(e -> deleteActiveConversation());

        leftActions.add(btnExportHtml);
        leftActions.add(btnExportTxt);
        leftActions.add(btnDelete);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightActions.setOpaque(false);
        btnClose.setFocusPainted(false);
        btnClose.addActionListener(e -> dispose());
        rightActions.add(btnClose);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(rightActions, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);
    }

    private void loadConversationList() {
        conversationListModel.clear();
        List<ChatHistoryManager.ConversationInfo> list = historyManager.getAllConversations();
        int selectedIndex = 0;
        int i = 0;
        for (ChatHistoryManager.ConversationInfo info : list) {
            conversationListModel.addElement(info);
            if (info.key().equals(activeKey)) {
                selectedIndex = i;
            }
            i++;
        }

        if (!conversationListModel.isEmpty()) {
            conversationJList.setSelectedIndex(selectedIndex);
            displayConversation(conversationListModel.get(selectedIndex).key(), null);
        } else {
            lblCurrentTitle.setText("Chưa có cuộc trò chuyện nào được lưu");
            lblCurrentCount.setText("");
            previewPane.setText("");
        }
    }

    private void displayConversation(String key, String highlightKeyword) {
        List<Message> messages = historyManager.getMessages(key);
        String title = ChatHistoryManager.GROUP_CONVERSATION_KEY.equals(key) ?
                ChatHistoryManager.GROUP_DISPLAY_NAME : ("Hội thoại: " + key);

        for (int i = 0; i < conversationListModel.size(); i++) {
            if (conversationListModel.get(i).key().equals(key)) {
                title = conversationListModel.get(i).displayName();
                break;
            }
        }

        lblCurrentTitle.setText(title);
        lblCurrentCount.setText(messages.size() + " tin nhắn");

        StringBuilder sb = new StringBuilder("<div id='chat-box'>");
        if (messages.isEmpty()) {
            sb.append("<div style='text-align:center; color:#94A3B8; margin-top:40px; font-size:13px;'>")
              .append("(Chưa có tin nhắn nào trong cuộc trò chuyện này)")
              .append("</div>");
        } else {
            for (Message m : messages) {
                sb.append(buildMessageHtml(m, highlightKeyword));
            }
        }
        sb.append("</div>");

        try {
            previewPane.setText("");
            editorKit.insertHTML(document, 0, sb.toString(), 0, 0, null);
            SwingUtilities.invokeLater(() -> previewPane.setCaretPosition(previewPane.getDocument().getLength()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeSearch() {
        String keyword = txtSearch.getText().trim();
        if (keyword.isEmpty()) {
            loadConversationList();
            return;
        }

        List<ChatHistoryManager.SearchResult> results = historyManager.searchMessages(keyword, null);
        lblCurrentTitle.setText("Kết quả tìm kiếm cho: \"" + keyword + "\"");
        lblCurrentCount.setText(results.size() + " kết quả tìm thấy");

        StringBuilder sb = new StringBuilder("<div id='chat-box'>");
        if (results.isEmpty()) {
            sb.append("<div style='text-align:center; color:#DC2626; margin-top:40px; font-size:13px;'>")
              .append("Không tìm thấy tin nhắn nào chứa từ khóa: <b>").append(escapeHtml(keyword)).append("</b>")
              .append("</div>");
        } else {
            for (ChatHistoryManager.SearchResult res : results) {
                sb.append("<div style='margin-bottom:12px; padding:8px 12px; background:#FFFFFF; border:1px solid #E2E8F0; border-radius:10px;'>")
                  .append("<div style='font-size:11px; color:#4F46E5; font-weight:bold; margin-bottom:4px;'>📌 ").append(escapeHtml(res.conversationName())).append("</div>")
                  .append(buildMessageHtml(res.message(), keyword))
                  .append("</div>");
            }
        }
        sb.append("</div>");

        try {
            previewPane.setText("");
            editorKit.insertHTML(document, 0, sb.toString(), 0, 0, null);
            SwingUtilities.invokeLater(() -> previewPane.setCaretPosition(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildMessageHtml(Message msg, String highlightKeyword) {
        String safeContent = escapeHtml(msg.getContent()).replace("\n", "<br>");
        if (highlightKeyword != null && !highlightKeyword.isEmpty()) {
            safeContent = highlightKeyword(safeContent, highlightKeyword);
        }

        String time = msg.getFormattedDateTime();
        String lockBadge = msg.isEncrypted() ? " [E2EE]" : "";

        if (msg.isMine()) {
            return String.format(
                    "<div class='msg-row-me'>" +
                    "  <div class='msg-bubble-me'>" +
                    "    <div>%s</div>" +
                    "    <div class='meta meta-me'>%s%s</div>" +
                    "  </div>" +
                    "</div>",
                    safeContent, time, lockBadge
            );
        } else {
            return String.format(
                    "<div class='msg-row-peer'>" +
                    "  <div class='msg-bubble-peer'>" +
                    "    <div class='sender'>%s</div>" +
                    "    <div>%s</div>" +
                    "    <div class='meta'>%s%s</div>" +
                    "  </div>" +
                    "</div>",
                    escapeHtml(msg.getSender()), safeContent, time, lockBadge
            );
        }
    }

    private String highlightKeyword(String text, String keyword) {
        if (keyword == null || keyword.isEmpty()) return text;
        String regex = "(?i)(" + java.util.regex.Pattern.quote(keyword) + ")";
        return text.replaceAll(regex, "<mark>$1</mark>");
    }

    private void exportActiveConversation(boolean asHtml) {
        if (activeKey == null) return;

        JFileChooser chooser = new JFileChooser();
        String ext = asHtml ? ".html" : ".txt";
        String defName = "LichSuChat_" + activeKey + "_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ext;
        chooser.setSelectedFile(new File(defName));
        chooser.setDialogTitle("Lưu file lịch sử cuộc trò chuyện");

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            boolean ok = asHtml ? historyManager.exportToHtml(activeKey, dest) : historyManager.exportToText(activeKey, dest);
            if (ok) {
                JOptionPane.showMessageDialog(this,
                        "Xuất lịch sử chat thành công!\nĐường dẫn: " + dest.getAbsolutePath(),
                        "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Không thể ghi file!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteActiveConversation() {
        if (activeKey == null) return;
        int opt = JOptionPane.showConfirmDialog(
                this,
                "Bạn có chắc chắn muốn xóa toàn bộ lịch sử của cuộc hội thoại này?\nThao tác này không thể hoàn tác!",
                "Xác nhận xóa lịch sử",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (opt == JOptionPane.YES_OPTION) {
            historyManager.clearConversation(activeKey);
            loadConversationList();
            JOptionPane.showMessageDialog(this, "Đã xóa sạch lịch sử hội thoại!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class ConversationRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(4, 2));
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(241, 245, 249)),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));

            if (value instanceof ChatHistoryManager.ConversationInfo info) {
                JLabel name = new JLabel(info.displayName());
                name.setFont(new Font("Segoe UI", Font.BOLD, 12));

                JLabel snippet = new JLabel(info.lastSnippet());
                snippet.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                snippet.setForeground(new Color(100, 116, 139));

                JLabel count = new JLabel(info.messageCount() + " tin");
                count.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                count.setForeground(new Color(148, 163, 184));

                p.add(name, BorderLayout.NORTH);
                p.add(snippet, BorderLayout.CENTER);
                p.add(count, BorderLayout.EAST);
            }

            if (isSelected) {
                p.setBackground(new Color(224, 231, 255));
            } else {
                p.setBackground(Color.WHITE);
            }
            return p;
        }
    }
}
