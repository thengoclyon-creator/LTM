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

    public Message(String sender, String content, MessageType type, boolean isMine, boolean isEncrypted) {
        this.id = System.currentTimeMillis() + "_" + Math.random();
        this.sender = sender;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.isMine = isMine;
        this.isEncrypted = isEncrypted;
    }

    public static Message userText(String sender, String content, boolean isMine, boolean isEncrypted) {
        return new Message(sender, content, MessageType.TEXT, isMine, isEncrypted);
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
        return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
    }
}
