package com.lan.chat.ui.components;

import com.lan.chat.model.Message;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;

public class ChatHistoryPanel extends JPanel {

    private final JTextPane textPane;
    private final HTMLEditorKit editorKit;
    private final HTMLDocument document;
    private final JScrollPane scrollPane;

    public ChatHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(248, 250, 252));

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html; charset=UTF-8");
        textPane.setBackground(new Color(248, 250, 252));

        editorKit = new HTMLEditorKit();
        textPane.setEditorKit(editorKit);
        document = (HTMLDocument) textPane.getDocument();

        // Modern, Bright, High-Contrast Light Theme Styles
        String css = """
            body {
                font-family: 'Segoe UI', 'Segoe UI Emoji', Tahoma, Arial, sans-serif;
                background-color: #F8FAFC;
                color: #1E293B;
                margin: 12px;
                padding: 0;
            }
            .msg-row-me {
                text-align: right;
                margin-bottom: 10px;
            }
            .msg-bubble-me {
                display: inline-block;
                background-color: #4F46E5;
                color: #FFFFFF;
                padding: 9px 14px;
                border-radius: 12px;
                max-width: 75%;
                text-align: left;
                word-wrap: break-word;
                font-size: 13px;
            }
            .msg-row-peer {
                text-align: left;
                margin-bottom: 10px;
            }
            .msg-bubble-peer {
                display: inline-block;
                background-color: #FFFFFF;
                color: #1E293B;
                padding: 9px 14px;
                border-radius: 12px;
                border: 1px solid #E2E8F0;
                max-width: 75%;
                text-align: left;
                word-wrap: break-word;
                font-size: 13px;
            }
            .msg-meta {
                font-size: 10px;
                color: #64748B;
                margin-top: 4px;
            }
            .msg-meta-me {
                color: #E0E7FF;
            }
            .sender-name {
                font-weight: bold;
                font-size: 11px;
                color: #4F46E5;
                margin-bottom: 3px;
            }
            .sender-name-me {
                font-weight: bold;
                font-size: 10px;
                color: #C7D2FE;
                margin-bottom: 2px;
            }
            .system-msg {
                text-align: center;
                margin: 8px 0;
            }
            .system-bubble {
                display: inline-block;
                background-color: #F1F5F9;
                color: #475569;
                padding: 4px 12px;
                border-radius: 10px;
                font-size: 11px;
                border: 1px solid #E2E8F0;
            }
            .system-error {
                background-color: #FEF2F2;
                border: 1px solid #FECACA;
                color: #DC2626;
                font-weight: bold;
            }
        """;
        editorKit.getStyleSheet().addRule(css);

        // Initial HTML skeleton
        try {
            editorKit.insertHTML(document, document.getLength(), "<div id='chat-box'></div>", 0, 0, null);
        } catch (Exception ignored) {}

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(new Color(248, 250, 252));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void appendMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                String html = buildMessageHtml(msg);
                editorKit.insertHTML(document, document.getLength(), html, 0, 0, null);
                scrollToBottom();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void appendSystem(String text, boolean isError) {
        SwingUtilities.invokeLater(() -> {
            try {
                String errorClass = isError ? " system-error" : "";
                String badge = isError ? "● " : "● ";
                String html = String.format(
                        "<div class='system-msg'><span class='system-bubble%s'>%s%s</span></div>",
                        errorClass, badge, escapeHtml(text)
                );
                editorKit.insertHTML(document, document.getLength(), html, 0, 0, null);
                scrollToBottom();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void renderConversation(java.util.List<Message> messages, String headerNotice) {
        SwingUtilities.invokeLater(() -> {
            try {
                document.remove(0, document.getLength());
                StringBuilder sb = new StringBuilder();
                sb.append("<div id='chat-box'>");
                if (headerNotice != null && !headerNotice.isEmpty()) {
                    sb.append("<div class='system-msg'><span class='system-bubble'>")
                      .append(escapeHtml(headerNotice))
                      .append("</span></div>");
                }
                if (messages != null) {
                    for (Message msg : messages) {
                        sb.append(buildMessageHtml(msg));
                    }
                }
                sb.append("</div>");
                editorKit.insertHTML(document, 0, sb.toString(), 0, 0, null);
                scrollToBottom();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String buildMessageHtml(Message msg) {
        String safeContent = escapeHtml(msg.getContent()).replace("\n", "<br>");
        String time = msg.getFormattedTime();
        String lockBadge = msg.isEncrypted() ? " [E2EE]" : "";

        if (msg.isMine()) {
            String myLabel = (msg.getSender() != null && !msg.getSender().isEmpty()) ? msg.getSender() : "Bạn";
            return String.format(
                    "<div class='msg-row-me'>" +
                    "  <div class='msg-bubble-me'>" +
                    "    <div class='sender-name-me'>%s</div>" +
                    "    <div>%s</div>" +
                    "    <div class='msg-meta msg-meta-me'>%s%s</div>" +
                    "  </div>" +
                    "</div>",
                    escapeHtml(myLabel), safeContent, time, lockBadge
            );
        } else {
            return String.format(
                    "<div class='msg-row-peer'>" +
                    "  <div class='msg-bubble-peer'>" +
                    "    <div class='sender-name'>%s</div>" +
                    "    <div>%s</div>" +
                    "    <div class='msg-meta'>%s%s</div>" +
                    "  </div>" +
                    "</div>",
                    escapeHtml(msg.getSender()), safeContent, time, lockBadge
            );
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vBar = scrollPane.getVerticalScrollBar();
            vBar.setValue(vBar.getMaximum());
        });
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            textPane.setText("");
        });
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
