package com.lan.chat.storage;

import com.lan.chat.model.ChatRoom;
import com.lan.chat.model.Message;
import com.lan.chat.model.MessageType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatStorageManager manages local persistence of conversation histories,
 * structured separately per channel (Group Chat vs individual Peer 1-1 Chat),
 * mimicking modern messaging applications like Zalo and Messenger.
 */
public class ChatStorageManager {

    private final File userStorageDir;

    public ChatStorageManager(String currentUserName) {
        String safeUser = sanitizeFileName(currentUserName == null || currentUserName.trim().isEmpty() ? "default_user" : currentUserName);
        File baseDir = new File("chat_history");
        this.userStorageDir = new File(baseDir, safeUser);
        if (!userStorageDir.exists()) {
            userStorageDir.mkdirs();
        }
    }

    /**
     * Appends a message to the channel's JSONL persistence file.
     */
    public synchronized void appendMessage(String channelKey, Message message) {
        if (channelKey == null || message == null) return;
        File file = getChannelFile(channelKey);
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
            bw.write(serializeMessage(message));
            bw.newLine();
        } catch (IOException e) {
            System.err.println("Lỗi lưu tin nhắn vào " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Loads all messages for a given channelKey.
     */
    public synchronized List<Message> loadMessages(String channelKey) {
        List<Message> list = new ArrayList<>();
        if (channelKey == null) return list;
        File file = getChannelFile(channelKey);
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Message msg = deserializeMessage(line);
                if (msg != null) {
                    list.add(msg);
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi đọc lịch sử tin nhắn từ " + file.getName() + ": " + e.getMessage());
        }
        return list;
    }

    /**
     * Clears/deletes conversation history for a channel.
     */
    public synchronized boolean clearHistory(String channelKey) {
        if (channelKey == null) return false;
        File file = getChannelFile(channelKey);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * Returns list of saved peer nicknames from existing history files.
     */
    public synchronized List<String> getSavedPeerKeys() {
        List<String> result = new ArrayList<>();
        File[] files = userStorageDir.listFiles((dir, name) -> name.startsWith("PEER_") && name.endsWith(".jsonl"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                // PEER_<name>.jsonl -> extract <name>
                String key = name.substring(5, name.length() - 6);
                result.add(key);
            }
        }
        return result;
    }

    private File getChannelFile(String channelKey) {
        String safeKey = sanitizeFileName(channelKey);
        return new File(userStorageDir, safeKey + ".jsonl");
    }

    public static String sanitizeFileName(String input) {
        if (input == null || input.isEmpty()) return "unnamed";
        return input.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static String serializeMessage(Message m) {
        return String.format(
                "{\"id\":\"%s\",\"sender\":\"%s\",\"content\":\"%s\",\"timestamp\":%d,\"type\":\"%s\",\"isMine\":%b,\"isEncrypted\":%b}",
                escapeJson(m.getId()),
                escapeJson(m.getSender()),
                escapeJson(m.getContent()),
                m.getTimestamp(),
                m.getType() != null ? m.getType().name() : "TEXT",
                m.isMine(),
                m.isEncrypted()
        );
    }

    private static Message deserializeMessage(String json) {
        try {
            String id = extractString(json, "id");
            String sender = extractString(json, "sender");
            String content = extractString(json, "content");
            long timestamp = extractLong(json, "timestamp", System.currentTimeMillis());
            String typeStr = extractString(json, "type");
            boolean isMine = extractBoolean(json, "isMine", false);
            boolean isEncrypted = extractBoolean(json, "isEncrypted", false);

            MessageType type = MessageType.TEXT;
            if (typeStr != null) {
                try {
                    type = MessageType.valueOf(typeStr);
                } catch (IllegalArgumentException ignored) {}
            }

            return new Message(id, sender, content, timestamp, type, isMine, isEncrypted);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static long extractLong(String json, String key, long def) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return def;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean extractBoolean(String json, String key, boolean def) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return def;
        start += pattern.length();
        if (json.startsWith("true", start)) return true;
        if (json.startsWith("false", start)) return false;
        return def;
    }

    // ==========================================
    // Phase 5: Room Persistence
    // ==========================================

    public synchronized void saveRooms(List<ChatRoom> rooms) {
        File file = new File(userStorageDir, "rooms.jsonl");
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8))) {
            if (rooms != null) {
                for (ChatRoom r : rooms) {
                    bw.write(serializeRoom(r));
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi lưu danh sách phòng: " + e.getMessage());
        }
    }

    public synchronized void saveOrUpdateRoom(ChatRoom room) {
        if (room == null) return;
        List<ChatRoom> existing = loadRooms();
        int foundIndex = -1;
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).getId().equals(room.getId())) {
                foundIndex = i;
                break;
            }
        }
        if (foundIndex >= 0) {
            existing.set(foundIndex, room);
        } else {
            existing.add(room);
        }
        saveRooms(existing);
    }

    public synchronized List<ChatRoom> loadRooms() {
        List<ChatRoom> list = new ArrayList<>();
        File file = new File(userStorageDir, "rooms.jsonl");
        if (!file.exists()) return list;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                ChatRoom room = deserializeRoom(line);
                if (room != null) {
                    list.add(room);
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi đọc danh sách phòng: " + e.getMessage());
        }
        return list;
    }

    public synchronized boolean deleteRoom(String roomId) {
        if (roomId == null) return false;
        List<ChatRoom> existing = loadRooms();
        boolean removed = existing.removeIf(r -> r.getId().equals(roomId));
        if (removed) {
            saveRooms(existing);
        }
        clearHistory("ROOM_" + roomId);
        return removed;
    }

    private static String serializeRoom(ChatRoom r) {
        return String.format(
                "{\"id\":\"%s\",\"name\":\"%s\",\"creator\":\"%s\",\"members\":\"%s\",\"createdAt\":%d}",
                escapeJson(r.getId()),
                escapeJson(r.getName()),
                escapeJson(r.getCreator()),
                escapeJson(String.join(",", r.getMembers())),
                r.getCreatedAt()
        );
    }

    private static ChatRoom deserializeRoom(String json) {
        try {
            String id = extractString(json, "id");
            String name = extractString(json, "name");
            String creator = extractString(json, "creator");
            String membersRaw = extractString(json, "members");
            long createdAt = extractLong(json, "createdAt", System.currentTimeMillis());

            List<String> members = new ArrayList<>();
            if (membersRaw != null && !membersRaw.isEmpty()) {
                for (String m : membersRaw.split(",")) {
                    if (!m.trim().isEmpty()) members.add(m.trim());
                }
            }
            return new ChatRoom(id, name, creator, members, createdAt);
        } catch (Exception e) {
            return null;
        }
    }
}
