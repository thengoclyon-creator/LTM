package com.lan.chat.network;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Peer;

public interface ConnectionListener {
    void onConnected(Peer peer);
    void onDisconnected(Peer peer, String reason);
    void onMessageReceived(Peer peer, String text, boolean isEncrypted);
    void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize);
    void onFileProgress(FileTransferTask task);
    void onFileCompleted(FileTransferTask task, boolean success, String message);
    void onStatusLog(String message, boolean isError);

    // Phase 3: Voice Call Signaling Callbacks
    void onCallOfferReceived(Peer peer, int remoteUdpPort);
    void onCallAccepted(Peer peer, int remoteUdpPort);
    void onCallRejected(Peer peer, String reason);
    void onCallHangup(Peer peer);
}
