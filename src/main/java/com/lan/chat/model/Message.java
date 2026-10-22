package com.lan.chat.model;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Message {
    private final String id;
    private final String sender;
    private final String content;
    private final long timestamp;
    private final MessageType type;
    private final boolean isMine;
    private final boolean isEncrypted;

    public Message(String id, String sender, String content, long timestamp, MessageType type, boolean isMine, boolean isEncrypted) {
        this.id = (id == null || id.isEmpty()) ? (System.currentTimeMillis() + "_" + Math.random()) : id;
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.type = type != null ? type : MessageType.TEXT;
        this.isMine = isMine;
        this.isEncrypted = isEncrypted;
    }

    public Message(String sender, String content, MessageType type, boolean isMine, boolean isEncrypted) {
        this(null, sender, content, System.currentTimeMillis(), type, isMine, isEncrypted);
    }

    public static Message userText(String sender, String content, boolean isMine, boolean isEncrypted) {
        return new Message(sender, content, MessageType.TEXT, isMine, isEncrypted);
    }

    public static Message voiceNote(String sender, String fileName, int durationSeconds, boolean isMine) {
        return new Message(sender, fileName + "|" + durationSeconds, MessageType.VOICE_NOTE, isMine, false);
    }

    public static Message fileTransfer(String sender, String fileId, String fileName, long fileSize, boolean isMine) {
        return new Message(sender, fileId + "|" + fileName + "|" + fileSize, MessageType.FILE_TRANSFER, isMine, false);
    }

    public static Message systemInfo(String text) {
        return new Message("Hệ thống", text, MessageType.SYSTEM_INFO, false, false);
    }

    public static Message systemError(String text) {
        return new Message("Lỗi", text, MessageType.SYSTEM_ERROR, false, false);
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public MessageType getType() { return type; }
    public boolean isMine() { return isMine; }
    public boolean isEncrypted() { return isEncrypted; }

    public String getFormattedTime() {
        SimpleDateFormat dayFmt = new SimpleDateFormat("yyyyMMdd");
        String msgDay = dayFmt.format(new Date(timestamp));
        String today = dayFmt.format(new Date());
        if (msgDay.equals(today)) {
            return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
        } else {
            return new SimpleDateFormat("dd/MM HH:mm").format(new Date(timestamp));
        }
    }
}
