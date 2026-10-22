package com.lan.chat.history;

import com.lan.chat.model.Message;
import com.lan.chat.model.MessageType;
import com.lan.chat.model.Peer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý lưu trữ, truy vấn, tìm kiếm và xuất lịch sử trò chuyện (Chat History).
 * Hỗ trợ lưu trữ riêng biệt cho Phòng Chat Chung (Group Chat) và Chat Riêng (1-1 Private Chat).
 */
public class ChatHistoryManager {

    public static final String GROUP_CONVERSATION_KEY = "ALL_LAN";
    public static final String GROUP_DISPLAY_NAME = "📢 Phòng Chat Chung (Mạng LAN)";

    private final File storageDir;
    private final Map<String, List<Message>> cache = new ConcurrentHashMap<>();

    public record ConversationInfo(
            String key,
            String displayName,
            int messageCount,
            long lastActivityTime,
            String lastSnippet
    ) {}

    public record SearchResult(
            String conversationKey,
            String conversationName,
            Message message
    ) {}

    public ChatHistoryManager() {
        this(new File("chat_history"));
    }

    public ChatHistoryManager(File storageDir) {
        this.storageDir = storageDir;
        if (!this.storageDir.exists()) {
            this.storageDir.mkdirs();
        }
    }

    /**
     * Sinh khóa hội thoại duy nhất cho Peer hoặc Phòng chung.
     */
    public static String getConversationKey(Peer peer) {
        if (peer == null) {
            return GROUP_CONVERSATION_KEY;
        }
        String safeNick = sanitizeFilename(peer.getNickname());
        String safeIp = peer.getIp().replace(".", "_").replace(":", "_");
        return "PRIVATE_" + safeNick + "_" + safeIp + "_" + peer.getPort();
    }

    public static String getDisplayName(Peer peer) {
        if (peer == null) {
            return GROUP_DISPLAY_NAME;
        }
        return "💬 " + peer.getNickname() + " (" + peer.getIp() + ":" + peer.getPort() + ")";
    }

    /**
     * Lưu 1 tin nhắn vào bộ nhớ đệm và ghi nối tiếp vào tệp tin trên đĩa.
     */
    public synchronized void saveMessage(String conversationKey, Message message) {
        if (conversationKey == null || conversationKey.trim().isEmpty() || message == null) return;

        // Cập nhật bộ nhớ đệm (Cache)
        List<Message> list = cache.computeIfAbsent(conversationKey, k -> loadMessagesFromFile(k));
        list.add(message);

        // Ghi vào file trên ổ đĩa (Append mode)
        File file = getFileForKey(conversationKey);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            String line = serializeMessage(message);
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            System.err.println("[ChatHistoryManager] Lỗi ghi lịch sử chat: " + e.getMessage());
        }
    }

    /**
     * Lấy toàn bộ danh sách tin nhắn của một cuộc hội thoại.
     */
    public synchronized List<Message> getMessages(String conversationKey) {
        if (conversationKey == null) return Collections.emptyList();
        return new ArrayList<>(cache.computeIfAbsent(conversationKey, this::loadMessagesFromFile));
    }

    /**
     * Tải danh sách tin nhắn từ file log.
     */
    private List<Message> loadMessagesFromFile(String conversationKey) {
        List<Message> list = new ArrayList<>();
        File file = getFileForKey(conversationKey);
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Message msg = deserializeMessage(line);
                if (msg != null) {
                    list.add(msg);
                }
            }
        } catch (IOException e) {
            System.err.println("[ChatHistoryManager] Lỗi đọc file lịch sử: " + e.getMessage());
        }
        return list;
    }

    /**
     * Lấy danh sách tóm tắt tất cả các cuộc trò chuyện đã có dữ liệu.
     */
    public synchronized List<ConversationInfo> getAllConversations() {
        List<ConversationInfo> result = new ArrayList<>();

        // Quét thư mục chat_history
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".log"));
        Set<String> keys = new LinkedHashSet<>();
        keys.add(GROUP_CONVERSATION_KEY); // Luôn ưu tiên hiển thị phòng chung

        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                String key = name.substring(0, name.length() - 4);
                keys.add(key);
            }
        }

        for (String key : keys) {
            List<Message> msgs = getMessages(key);
            int count = msgs.size();
            long lastTime = 0;
            String lastSnippet = "(Chưa có tin nhắn)";
            if (!msgs.isEmpty()) {
                Message last = msgs.get(msgs.size() - 1);
                lastTime = last.getTimestamp();
                String raw = last.getContent();
                lastSnippet = raw.length() > 40 ? raw.substring(0, 37) + "..." : raw;
            }

            String display;
            if (GROUP_CONVERSATION_KEY.equals(key)) {
                display = GROUP_DISPLAY_NAME;
            } else if (key.startsWith("PRIVATE_")) {
                String sub = key.substring("PRIVATE_".length());
                String[] parts = sub.split("_");
                String nick = parts.length > 0 ? parts[0] : sub;
                display = "💬 Chat Riêng: " + nick;
            } else {
                display = "💬 " + key;
            }

            result.add(new ConversationInfo(key, display, count, lastTime, lastSnippet));
        }

        // Sắp xếp: Phòng chung đầu tiên, sau đó theo thời gian hoạt động mới nhất
        result.sort((a, b) -> {
            if (a.key().equals(GROUP_CONVERSATION_KEY)) return -1;
            if (b.key().equals(GROUP_CONVERSATION_KEY)) return 1;
            return Long.compare(b.lastActivityTime(), a.lastActivityTime());
        });

        return result;
    }

    /**
     * Tìm kiếm tin nhắn theo từ khóa trên toàn bộ các cuộc hội thoại hoặc một hội thoại cụ thể.
     */
    public synchronized List<SearchResult> searchMessages(String keyword, String targetConversationKey) {
        List<SearchResult> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return results;
        String query = keyword.toLowerCase().trim();

        List<String> keysToSearch = new ArrayList<>();
        if (targetConversationKey != null && !targetConversationKey.isEmpty()) {
            keysToSearch.add(targetConversationKey);
        } else {
            for (ConversationInfo c : getAllConversations()) {
                keysToSearch.add(c.key());
            }
        }

        for (String key : keysToSearch) {
            List<Message> msgs = getMessages(key);
            String convName = key.equals(GROUP_CONVERSATION_KEY) ? GROUP_DISPLAY_NAME : ("Chat: " + key);
            for (Message m : msgs) {
                if (m.getContent() != null && m.getContent().toLowerCase().contains(query)) {
                    results.add(new SearchResult(key, convName, m));
                } else if (m.getSender() != null && m.getSender().toLowerCase().contains(query)) {
                    results.add(new SearchResult(key, convName, m));
                }
            }
        }
        return results;
    }

    /**
     * Xuất lịch sử ra tệp tin TXT văn bản có định dạng chuẩn.
     */
    public synchronized boolean exportToText(String conversationKey, File targetFile) {
        List<Message> messages = getMessages(conversationKey);
        String title = GROUP_CONVERSATION_KEY.equals(conversationKey) ? GROUP_DISPLAY_NAME : ("Hội thoại: " + conversationKey);

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            bw.write("========================================================================\r\n");
            bw.write("   LAN CHAT PRO - NHẬT KÝ LỊCH SỬ TIN NHẮN (CHAT HISTORY)\r\n");
            bw.write("   Cuộc trò chuyện: " + title + "\r\n");
            bw.write("   Thời gian xuất : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\r\n");
            bw.write("   Tổng số tin    : " + messages.size() + " tin nhắn\r\n");
            bw.write("========================================================================\r\n\r\n");

            for (Message m : messages) {
                String e2eeBadge = m.isEncrypted() ? " [Mã hóa E2EE]" : "";
                String role = m.isMine() ? " [Bạn]" : " [" + m.getSender() + "]";
                bw.write(String.format("[%s]%s%s: %s\r\n", m.getFormattedDateTime(), role, e2eeBadge, m.getContent()));
            }

            bw.write("\r\n=========================== HẾT NHẬT KÝ ===========================\r\n");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Xuất lịch sử ra tệp tin HTML giao diện hiện đại, sẵn sàng in hoặc xem lại.
     */
    public synchronized boolean exportToHtml(String conversationKey, File targetFile) {
        List<Message> messages = getMessages(conversationKey);
        String title = GROUP_CONVERSATION_KEY.equals(conversationKey) ? GROUP_DISPLAY_NAME : ("Hội thoại: " + conversationKey);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang='vi'>\n<head>\n")
          .append("<meta charset='UTF-8'>\n")
          .append("<title>Lịch Sử Chat - ").append(escapeHtml(title)).append("</title>\n")
          .append("<style>\n")
          .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px; color: #1e293b; }\n")
          .append(".container { max-width: 820px; margin: 0 auto; background: #ffffff; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.06); padding: 24px; }\n")
          .append(".header { border-bottom: 2px solid #e2e8f0; padding-bottom: 16px; margin-bottom: 20px; }\n")
          .append(".header h1 { font-size: 20px; margin: 0 0 6px 0; color: #4f46e5; }\n")
          .append(".header p { margin: 0; font-size: 13px; color: #64748b; }\n")
          .append(".chat-list { display: flex; flex-direction: column; gap: 12px; }\n")
          .append(".row { display: flex; flex-direction: column; }\n")
          .append(".row.me { align-items: flex-end; }\n")
          .append(".row.peer { align-items: flex-start; }\n")
          .append(".bubble { max-width: 75%; padding: 10px 16px; border-radius: 14px; font-size: 14px; line-height: 1.5; word-wrap: break-word; }\n")
          .append(".bubble.me { background: #4f46e5; color: #ffffff; border-bottom-right-radius: 4px; }\n")
          .append(".bubble.peer { background: #f8fafc; color: #1e293b; border: 1px solid #e2e8f0; border-bottom-left-radius: 4px; }\n")
          .append(".meta { font-size: 11px; margin-top: 4px; }\n")
          .append(".meta.me { color: #e0e7ff; text-align: right; }\n")
          .append(".meta.peer { color: #94a3b8; }\n")
          .append(".sender { font-weight: bold; font-size: 12px; color: #4f46e5; margin-bottom: 4px; }\n")
          .append(".badge { display: inline-block; background: rgba(16,185,129,0.15); color: #059669; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: bold; margin-left: 6px; }\n")
          .append("</style>\n</head>\n<body>\n")
          .append("<div class='container'>\n")
          .append("  <div class='header'>\n")
          .append("    <h1>⚡ ").append(escapeHtml(title)).append("</h1>\n")
          .append("    <p>Thời gian xuất: ").append(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()))
          .append(" &bull; Tổng số tin nhắn: <b>").append(messages.size()).append("</b></p>\n")
          .append("  </div>\n")
          .append("  <div class='chat-list'>\n");

        for (Message m : messages) {
            String roleClass = m.isMine() ? "me" : "peer";
            String e2eeBadge = m.isEncrypted() ? "<span class='badge'>🔒 AES E2EE</span>" : "";
            sb.append("    <div class='row ").append(roleClass).append("'>\n")
              .append("      <div class='bubble ").append(roleClass).append("'>\n");

            if (!m.isMine()) {
                sb.append("        <div class='sender'>").append(escapeHtml(m.getSender())).append("</div>\n");
            }
            sb.append("        <div>").append(escapeHtml(m.getContent()).replace("\n", "<br>")).append("</div>\n")
              .append("        <div class='meta ").append(roleClass).append("'>")
              .append(m.getFormattedTime()).append(e2eeBadge).append("</div>\n")
              .append("      </div>\n")
              .append("    </div>\n");
        }

        sb.append("  </div>\n</div>\n</body>\n</html>");

        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            bw.write(sb.toString());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Xóa sạch lịch sử của một cuộc trò chuyện.
     */
    public synchronized boolean clearConversation(String conversationKey) {
        cache.remove(conversationKey);
        File file = getFileForKey(conversationKey);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    private File getFileForKey(String key) {
        String safeName = sanitizeFilename(key);
        return new File(storageDir, safeName + ".log");
    }

    private String serializeMessage(Message m) {
        String safeContent = m.getContent() == null ? "" : m.getContent()
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");

        return m.getTimestamp() + "\t"
                + m.getFormattedDateTime() + "\t"
                + m.getId() + "\t"
                + (m.getSender() == null ? "" : m.getSender()) + "\t"
                + m.isMine() + "\t"
                + m.isEncrypted() + "\t"
                + (m.getType() == null ? MessageType.TEXT : m.getType().name()) + "\t"
                + safeContent;
    }

    private Message deserializeMessage(String line) {
        try {
            String[] parts = line.split("\t", 8);
            if (parts.length < 8) return null;

            long timestamp = Long.parseLong(parts[0].trim());
            // parts[1] is human-readable date string
            String id = parts[2].trim();
            String sender = parts[3];
            boolean isMine = Boolean.parseBoolean(parts[4].trim());
            boolean isEncrypted = Boolean.parseBoolean(parts[5].trim());
            MessageType type = MessageType.valueOf(parts[6].trim());
            String rawContent = parts[7]
                    .replace("\\t", "\t")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\");

            return new Message(id, sender, rawContent, timestamp, type, isMine, isEncrypted);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
