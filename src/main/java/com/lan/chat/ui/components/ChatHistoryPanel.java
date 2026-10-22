package com.lan.chat.ui.components;

import com.lan.chat.model.Message;

import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.io.StringWriter;

public class ChatHistoryPanel extends JPanel {

    private final JTextPane textPane;
    private final HTMLEditorKit editorKit;
    private final HTMLDocument document;
    private final JScrollPane scrollPane;

    public ChatHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(49, 51, 56));

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setContentType("text/html; charset=UTF-8");

        editorKit = new HTMLEditorKit();
        textPane.setEditorKit(editorKit);
        document = (HTMLDocument) textPane.getDocument();

        // Base styles for chat history
        String css = """
            body {
                font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                background-color: #313338;
                color: #DBDEE1;
                margin: 8px;
                padding: 0;
            }
            .msg-row-me {
                text-align: right;
                margin-bottom: 8px;
            }
            .msg-bubble-me {
                display: inline-block;
                background-color: #5865F2;
                color: #FFFFFF;
                padding: 8px 12px;
                border-radius: 12px;
                max-width: 75%;
                text-align: left;
                word-wrap: break-word;
            }
            .msg-row-peer {
                text-align: left;
                margin-bottom: 8px;
            }
            .msg-bubble-peer {
                display: inline-block;
                background-color: #2B2D31;
                color: #DBDEE1;
                padding: 8px 12px;
                border-radius: 12px;
                border: 1px solid #3F4147;
                max-width: 75%;
                text-align: left;
                word-wrap: break-word;
            }
            .msg-meta {
                font-size: 9px;
                color: #949BA4;
                margin-top: 3px;
            }
            .msg-meta-me {
                color: #E0E3FF;
            }
            .sender-name {
                font-weight: bold;
                font-size: 11px;
                color: #5865F2;
                margin-bottom: 2px;
            }
            .system-msg {
                text-align: center;
                margin: 6px 0;
            }
            .system-bubble {
                display: inline-block;
                background-color: #232428;
                color: #949BA4;
                padding: 4px 10px;
                border-radius: 10px;
                font-size: 11px;
                border: 1px solid #35373C;
            }
            .system-error {
                color: #ED4245;
                font-weight: bold;
            }
            .file-card {
                display: inline-block;
                background-color: #232428;
                border: 1px dashed #5865F2;
                padding: 8px 12px;
                border-radius: 8px;
                margin-top: 4px;
            }
        """;
        editorKit.getStyleSheet().addRule(css);

        // Initial HTML skeleton
        try {
            editorKit.insertHTML(document, document.getLength(), "<div id='chat-box'></div>", 0, 0, null);
        } catch (Exception ignored) {}

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
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
                String icon = isError ? "⚠️ " : "ℹ️ ";
                String html = String.format(
                        "<div class='system-msg'><span class='system-bubble%s'>%s%s</span></div>",
                        errorClass, icon, escapeHtml(text)
                );
                editorKit.insertHTML(document, document.getLength(), html, 0, 0, null);
                scrollToBottom();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String buildMessageHtml(Message msg) {
        String safeContent = escapeHtml(msg.getContent()).replace("\n", "<br>");
        String time = msg.getFormattedTime();
        String lockIcon = msg.isEncrypted() ? " 🔒" : "";

        if (msg.isMine()) {
            return String.format(
                    "<div class='msg-row-me'>" +
                    "  <div class='msg-bubble-me'>" +
                    "    <div>%s</div>" +
                    "    <div class='msg-meta msg-meta-me'>%s%s</div>" +
                    "  </div>" +
                    "</div>",
                    safeContent, time, lockIcon
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
                    escapeHtml(msg.getSender()), safeContent, time, lockIcon
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
