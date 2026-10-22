package com.lan.chat.model;

/**
 * Protocol packet types transmitted over TCP DataOutputStream/DataInputStream.
 * Designed to cleanly support Phase 1 (Text & Ping), Phase 2 (Chunked & Resumed File Transfer),
 * Phase 3 (Voice Signaling), and Phase 4 (Encrypted payloads).
 */
public class PacketType {
    // Phase 1: Core Text & Connection handshake
    public static final int HANDSHAKE = 100;        // Exchange nickname & client info
    public static final int HANDSHAKE_ACK = 101;    // Handshake acknowledgement
    public static final int CHAT_TEXT = 102;        // Text message (UTF-8 or AES encrypted)
    public static final int PING = 103;             // Keepalive ping
    public static final int PONG = 104;             // Keepalive pong
    public static final int DISCONNECT = 105;       // Graceful disconnect notify

    // Phase 2: High-Performance Resumable Chunked File Transfer
    public static final int FILE_OFFER = 200;       // fileId, fileName, totalSize, checksum
    public static final int FILE_ACCEPT = 201;      // fileId, startByteOffset (for RESUME!)
    public static final int FILE_REJECT = 202;      // fileId, reason
    public static final int FILE_CHUNK = 203;       // fileId, chunkOffset, chunkLength, byte[]
    public static final int FILE_COMPLETE = 204;    // fileId, status, message
    public static final int FILE_CANCEL = 205;      // fileId
    public static final int FILE_PAUSE = 206;       // fileId
    public static final int FILE_RESUME_REQ = 207;  // fileId, startByteOffset

    // Phase 3: Voice Call Signaling (UDP voice stream port negotiation)
    public static final int CALL_OFFER = 300;       // callerNickname, callerUdpPort
    public static final int CALL_ACCEPT = 301;      // calleeNickname, calleeUdpPort
    public static final int CALL_REJECT = 302;      // reason
    public static final int CALL_HANGUP = 303;      // notify end of call
}
