package com.lan.chat.network;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Peer;

public interface ConnectionListener {
    default void onPeerDiscovered(Peer peer) {}
    void onConnected(Peer peer);
    void onDisconnected(Peer peer, String reason);
    void onMessageReceived(Peer peer, String text, boolean isEncrypted, boolean isGroup);
    default void onMessageReceived(Peer peer, String text, boolean isEncrypted) {
        onMessageReceived(peer, text, isEncrypted, true);
    }
    void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize);
    void onFileProgress(FileTransferTask task);
    void onFileCompleted(FileTransferTask task, boolean success, String message);
    default void onVoiceNoteReceived(Peer peer, String fileName, int durationSeconds, byte[] audioData, boolean isGroup) {}
    void onStatusLog(String message, boolean isError);

    // Phase 3: Voice Call Signaling Callbacks
    void onCallOfferReceived(Peer peer, int remoteUdpPort);
    void onCallAccepted(Peer peer, int remoteUdpPort);
    void onCallRejected(Peer peer, String reason);
    void onCallHangup(Peer peer);

    // Phase 5: Custom Group Room Callbacks
    default void onRoomInviteReceived(com.lan.chat.model.ChatRoom room, String invitedBy) {}
    default void onRoomRenamed(String roomId, String newRoomName, String updatedBy) {}
    default void onRoomMembersUpdated(String roomId, java.util.List<String> newMembers, String updatedBy) {}
    default void onRoomMessageReceived(String roomId, String senderNickname, String text, boolean isEncrypted) {}
    default void onRoomVoiceNoteReceived(String roomId, String senderNickname, String fileName, int durationSeconds, byte[] audioData) {}
}
