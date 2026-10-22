package com.lan.chat.network;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.PacketType;
import com.lan.chat.model.Peer;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PeerConnection manages a single TCP connection with a remote peer.
 * Handles the background reading thread (while(true) reading DataInputStream),
 * message dispatch, chunked resumable file transfer (large GB files),
 * and Voice Call signaling (UDP port coordination).
 */
public class PeerConnection {

    private final Socket socket;
    private final Peer peer;
    private final String myNickname;
    private final int myListenPort;
    private final ConnectionListener listener;

    private DataInputStream dis;
    private DataOutputStream dos;
    private final Object sendLock = new Object();

    private volatile boolean running = false;
    private Thread readerThread;

    // Active file tasks
    private final Map<String, FileTransferTask> activeTasks = new ConcurrentHashMap<>();
    private File downloadFolder = new File(System.getProperty("user.home"), "Downloads");

    public PeerConnection(Socket socket, String myNickname, int myListenPort, ConnectionListener listener) {
        this.socket = socket;
        this.myNickname = myNickname;
        this.myListenPort = myListenPort;
        this.listener = listener;

        String remoteIp = socket.getInetAddress().getHostAddress();
        int remotePort = socket.getPort();
        this.peer = new Peer(remoteIp, remotePort, remoteIp);
    }

    public PeerConnection(Socket socket, String myNickname, ConnectionListener listener) {
        this(socket, myNickname, 5000, listener);
    }

    public void setDownloadFolder(File folder) {
        if (folder != null && folder.exists() && folder.isDirectory()) {
            this.downloadFolder = folder;
        }
    }

    public File getDownloadFolder() {
        return downloadFolder;
    }

    public Peer getPeer() {
        return peer;
    }

    public boolean isConnected() {
        return running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Start the connection streams and reader thread.
     */
    public void start() throws IOException {
        this.dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.running = true;

        // Start background reader thread
        readerThread = new Thread(this::readLoop, "PeerReader-" + peer.getAddressKey());
        readerThread.setDaemon(true);
        readerThread.start();

        // Send handshake packet with our nickname
        sendHandshake();
    }

    private void sendHandshake() {
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.HANDSHAKE);
                dos.writeUTF(myNickname);
                dos.writeInt(myListenPort);
                dos.flush();
            }
        } catch (IOException e) {
            listener.onStatusLog("Lỗi gửi handshake: " + e.getMessage(), true);
        }
    }

    /**
     * Core multi-threaded loop: continuously reads DataInputStream.
     */
    private void readLoop() {
        peer.setConnected(true);

        try {
            while (running && !socket.isClosed()) {
                int packetType = dis.readInt();

                switch (packetType) {
                    case PacketType.HANDSHAKE -> handleHandshake();
                    case PacketType.HANDSHAKE_ACK -> handleHandshakeAck();
                    case PacketType.CHAT_TEXT -> handleChatText();
                    case PacketType.PING -> handlePing();
                    case PacketType.PONG -> handlePong();
                    case PacketType.DISCONNECT -> handleDisconnectPacket();

                    // File Transfer Protocols
                    case PacketType.FILE_OFFER -> handleFileOffer();
                    case PacketType.FILE_ACCEPT -> handleFileAccept();
                    case PacketType.FILE_REJECT -> handleFileReject();
                    case PacketType.FILE_CHUNK -> handleFileChunk();
                    case PacketType.FILE_COMPLETE -> handleFileComplete();
                    case PacketType.FILE_CANCEL -> handleFileCancel();
                    case PacketType.FILE_PAUSE -> handleFilePause();
                    case PacketType.FILE_RESUME_REQ -> handleFileResumeReq();

                    // Voice Call Signaling Protocols
                    case PacketType.CALL_OFFER -> handleCallOffer();
                    case PacketType.CALL_ACCEPT -> handleCallAccept();
                    case PacketType.CALL_REJECT -> handleCallReject();
                    case PacketType.CALL_HANGUP -> handleCallHangup();

                    default -> listener.onStatusLog("Nhận packet type lạ: " + packetType, false);
                }
            }
        } catch (EOFException | SocketException e) {
            // Normal connection close or cable disconnect
            if (running) {
                // Pause any in-progress file transfer and persist resume metadata!
                pauseAllTransfersOnDisconnect();
                listener.onDisconnected(peer, "Mất kết nối với " + peer.getNickname() + " (" + e.getMessage() + ")");
            }
        } catch (Exception e) {
            if (running) {
                pauseAllTransfersOnDisconnect();
                listener.onStatusLog("Lỗi luồng đọc dữ liệu: " + e.getMessage(), true);
                listener.onDisconnected(peer, e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void handleHandshake() throws IOException {
        String remoteName = dis.readUTF();
        int remoteListenPort = dis.readInt();
        peer.setNickname(remoteName);
        peer.setPort(remoteListenPort);
        listener.onConnected(peer);
        listener.onStatusLog("Đã kết nối với " + remoteName + " [" + peer.getIp() + ":" + remoteListenPort + "]", false);

        // Reply ACK
        synchronized (sendLock) {
            dos.writeInt(PacketType.HANDSHAKE_ACK);
            dos.writeUTF(myNickname);
            dos.writeInt(myListenPort);
            dos.flush();
        }
    }

    private void handleHandshakeAck() throws IOException {
        String remoteName = dis.readUTF();
        int remoteListenPort = dis.readInt();
        peer.setNickname(remoteName);
        peer.setPort(remoteListenPort);
        listener.onConnected(peer);
        listener.onStatusLog("Bắt tay thành công với " + remoteName, false);
    }

    private void handleChatText() throws IOException {
        boolean isEncrypted = dis.readBoolean();
        String text = dis.readUTF();
        listener.onMessageReceived(peer, text, isEncrypted);
    }

    private void handlePing() throws IOException {
        synchronized (sendLock) {
            dos.writeInt(PacketType.PONG);
            dos.flush();
        }
    }

    private void handlePong() {
        peer.setLastSeen(System.currentTimeMillis());
    }

    private void handleDisconnectPacket() {
        listener.onDisconnected(peer, "Đối phương chủ động ngắt kết nối.");
        close();
    }

    // ==========================================
    // Phase 2: Resumable Chunked File Transfer
    // ==========================================

    private void handleFileOffer() throws IOException {
        String fileId = dis.readUTF();
        String fileName = dis.readUTF();
        long totalSize = dis.readLong();

        listener.onFileOfferReceived(peer, fileId, fileName, totalSize);
    }

    private void handleFileAccept() throws IOException {
        String fileId = dis.readUTF();
        long startOffset = dis.readLong();

        FileTransferTask task = activeTasks.get(fileId);
        if (task != null && task.isUpload()) {
            listener.onStatusLog("Đối phương chấp nhận nhận file " + task.getFileName()
                    + (startOffset > 0 ? " (Tự động nối tiếp từ " + startOffset + " bytes)" : ""), false);
            // Start upload thread from startOffset
            new Thread(() -> runUpload(task, startOffset), "UploadThread-" + fileId).start();
        }
    }

    private void handleFileReject() throws IOException {
        String fileId = dis.readUTF();
        String reason = dis.readUTF();
        FileTransferTask task = activeTasks.remove(fileId);
        if (task != null) {
            task.setStatus(FileTransferTask.Status.CANCELLED);
            listener.onFileCompleted(task, false, "Đối phương từ chối nhận file: " + reason);
        }
    }

    private void handleFileChunk() throws IOException {
        String fileId = dis.readUTF();
        long chunkOffset = dis.readLong();
        int chunkLen = dis.readInt();
        byte[] chunk = new byte[chunkLen];
        dis.readFully(chunk);

        FileTransferTask task = activeTasks.get(fileId);
        if (task != null && !task.isUpload()) {
            File partFile = task.getPartFile();
            try (RandomAccessFile raf = new RandomAccessFile(partFile, "rw")) {
                raf.seek(chunkOffset);
                raf.write(chunk, 0, chunkLen);
            }
            long newTransferred = chunkOffset + chunkLen;
            task.updateProgress(newTransferred);

            // Persist resume metadata every 1MB or on completion
            if (newTransferred % (1024 * 1024) < chunkLen || newTransferred >= task.getTotalBytes()) {
                task.saveResumeMetadata();
            }

            listener.onFileProgress(task);
        }
    }

    private void handleFileComplete() throws IOException {
        String fileId = dis.readUTF();
        boolean success = dis.readBoolean();
        String msg = dis.readUTF();

        FileTransferTask task = activeTasks.remove(fileId);
        if (task != null) {
            if (!task.isUpload() && success) {
                // Rename .part file to final file name!
                File partFile = task.getPartFile();
                File targetFile = task.getLocalFile();
                if (targetFile.exists()) targetFile.delete();
                partFile.renameTo(targetFile);
                task.cleanResumeFiles();
            }
            task.setStatus(success ? FileTransferTask.Status.COMPLETED : FileTransferTask.Status.FAILED);
            listener.onFileCompleted(task, success, msg);
        }
    }

    private void handleFileCancel() throws IOException {
        String fileId = dis.readUTF();
        FileTransferTask task = activeTasks.remove(fileId);
        if (task != null) {
            task.setStatus(FileTransferTask.Status.CANCELLED);
            listener.onFileCompleted(task, false, "Tiến trình truyền file đã bị hủy.");
        }
    }

    private void handleFilePause() throws IOException {
        String fileId = dis.readUTF();
        FileTransferTask task = activeTasks.get(fileId);
        if (task != null) {
            task.setStatus(FileTransferTask.Status.PAUSED);
            task.saveResumeMetadata();
            listener.onStatusLog("Tiến trình truyền file " + task.getFileName() + " đã tạm dừng.", false);
        }
    }

    private void handleFileResumeReq() throws IOException {
        String fileId = dis.readUTF();
        long startOffset = dis.readLong();
        FileTransferTask task = activeTasks.get(fileId);
        if (task != null && task.isUpload()) {
            listener.onStatusLog("Tiếp tục gửi file " + task.getFileName() + " từ offset: " + startOffset + " bytes", false);
            new Thread(() -> runUpload(task, startOffset), "ResumeUploadThread-" + fileId).start();
        }
    }

    private void pauseAllTransfersOnDisconnect() {
        for (FileTransferTask task : activeTasks.values()) {
            if (task.getStatus() == FileTransferTask.Status.TRANSFERRING) {
                task.setStatus(FileTransferTask.Status.PAUSED);
                task.saveResumeMetadata();
            }
        }
    }

    // ==========================================
    // Phase 3: Voice Call Signaling Handlers
    // ==========================================

    private void handleCallOffer() throws IOException {
        int remoteUdpPort = dis.readInt();
        listener.onCallOfferReceived(peer, remoteUdpPort);
    }

    private void handleCallAccept() throws IOException {
        int remoteUdpPort = dis.readInt();
        listener.onCallAccepted(peer, remoteUdpPort);
    }

    private void handleCallReject() throws IOException {
        String reason = dis.readUTF();
        listener.onCallRejected(peer, reason);
    }

    private void handleCallHangup() {
        listener.onCallHangup(peer);
    }

    // ==========================================
    // Public Sending & Action API
    // ==========================================

    /**
     * Send text message to peer.
     */
    public boolean sendTextMessage(String message, boolean isEncrypted) {
        if (!isConnected()) return false;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.CHAT_TEXT);
                dos.writeBoolean(isEncrypted);
                dos.writeUTF(message);
                dos.flush();
            }
            return true;
        } catch (IOException e) {
            listener.onStatusLog("Lỗi gửi tin nhắn: " + e.getMessage(), true);
            return false;
        }
    }

    /**
     * Offer a file for transfer to peer.
     */
    public FileTransferTask offerFile(File file) {
        if (!isConnected() || file == null || !file.exists()) return null;

        String fileId = System.currentTimeMillis() + "_" + Math.abs(file.getName().hashCode());
        FileTransferTask task = new FileTransferTask(fileId, file.getName(), file.length(), true, file);
        activeTasks.put(fileId, task);

        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.FILE_OFFER);
                dos.writeUTF(fileId);
                dos.writeUTF(file.getName());
                dos.writeLong(file.length());
                dos.flush();
            }
            listener.onStatusLog("Đang mời " + peer.getNickname() + " nhận file: " + file.getName(), false);
            return task;
        } catch (IOException e) {
            activeTasks.remove(fileId);
            listener.onStatusLog("Lỗi gửi lời mời nhận file: " + e.getMessage(), true);
            return null;
        }
    }

    /**
     * Accept incoming file offer (with automatic Resume check!).
     */
    public FileTransferTask acceptFileOffer(String fileId, String fileName, long totalSize) {
        if (!isConnected()) return null;

        File targetFile = new File(downloadFolder, fileName);
        File partFile = new File(targetFile.getAbsolutePath() + ".part");

        long existingBytes = 0;
        if (partFile.exists()) {
            existingBytes = partFile.length();
            if (existingBytes >= totalSize) {
                existingBytes = 0;
                partFile.delete();
            }
        }

        FileTransferTask task = new FileTransferTask(fileId, fileName, totalSize, false, targetFile);
        task.updateProgress(existingBytes);
        activeTasks.put(fileId, task);

        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.FILE_ACCEPT);
                dos.writeUTF(fileId);
                dos.writeLong(existingBytes); // Inform sender where to resume from!
                dos.flush();
            }
            listener.onStatusLog("Chấp nhận tải " + fileName + (existingBytes > 0 ? " (Tự động nối từ " + existingBytes + " B)" : ""), false);
            return task;
        } catch (IOException e) {
            activeTasks.remove(fileId);
            listener.onStatusLog("Lỗi gửi accept file: " + e.getMessage(), true);
            return null;
        }
    }

    /**
     * Reject incoming file offer.
     */
    public void rejectFileOffer(String fileId, String reason) {
        if (!isConnected()) return;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.FILE_REJECT);
                dos.writeUTF(fileId);
                dos.writeUTF(reason != null ? reason : "Người nhận đã từ chối.");
                dos.flush();
            }
        } catch (IOException ignored) {}
    }

    /**
     * Pause an ongoing file transfer and tell remote peer.
     */
    public void pauseFileTransfer(String fileId) {
        FileTransferTask task = activeTasks.get(fileId);
        if (task != null) {
            task.setStatus(FileTransferTask.Status.PAUSED);
            task.saveResumeMetadata();
            try {
                synchronized (sendLock) {
                    dos.writeInt(PacketType.FILE_PAUSE);
                    dos.writeUTF(fileId);
                    dos.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Resume a paused file transfer.
     */
    public void resumeFileTransfer(String fileId) {
        FileTransferTask task = activeTasks.get(fileId);
        if (task != null && !task.isUpload()) {
            long existing = task.getPartFile().exists() ? task.getPartFile().length() : 0;
            task.setStatus(FileTransferTask.Status.TRANSFERRING);
            try {
                synchronized (sendLock) {
                    dos.writeInt(PacketType.FILE_RESUME_REQ);
                    dos.writeUTF(fileId);
                    dos.writeLong(existing);
                    dos.flush();
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Worker thread to stream file chunks with 8KB buffer and resume seek.
     */
    private void runUpload(FileTransferTask task, long startOffset) {
        task.setStatus(FileTransferTask.Status.TRANSFERRING);
        task.updateProgress(startOffset);
        listener.onFileProgress(task);

        byte[] buffer = new byte[8192]; // 8KB Chunking as specified
        long currentOffset = startOffset;

        try (RandomAccessFile raf = new RandomAccessFile(task.getLocalFile(), "r")) {
            raf.seek(startOffset);

            while (running && task.getStatus() == FileTransferTask.Status.TRANSFERRING && currentOffset < task.getTotalBytes()) {
                int bytesToRead = (int) Math.min(buffer.length, task.getTotalBytes() - currentOffset);
                int bytesRead = raf.read(buffer, 0, bytesToRead);
                if (bytesRead <= 0) break;

                synchronized (sendLock) {
                    dos.writeInt(PacketType.FILE_CHUNK);
                    dos.writeUTF(task.getFileId());
                    dos.writeLong(currentOffset);
                    dos.writeInt(bytesRead);
                    dos.write(buffer, 0, bytesRead);
                    dos.flush();
                }

                currentOffset += bytesRead;
                task.updateProgress(currentOffset);
                listener.onFileProgress(task);
            }

            if (task.getStatus() == FileTransferTask.Status.TRANSFERRING && currentOffset >= task.getTotalBytes()) {
                // Transfer completed successfully
                task.setStatus(FileTransferTask.Status.COMPLETED);
                synchronized (sendLock) {
                    dos.writeInt(PacketType.FILE_COMPLETE);
                    dos.writeUTF(task.getFileId());
                    dos.writeBoolean(true);
                    dos.writeUTF("Truyền file hoàn tất 100%");
                    dos.flush();
                }
                activeTasks.remove(task.getFileId());
                listener.onFileCompleted(task, true, "Đã gửi file " + task.getFileName() + " thành công!");
            }

        } catch (SocketException se) {
            task.setStatus(FileTransferTask.Status.PAUSED);
            task.saveResumeMetadata();
            listener.onStatusLog("Mất kết nối mạng khi gửi file (Đã lưu offset để tự động nối): " + se.getMessage(), true);
            listener.onFileCompleted(task, false, "Đứt kết nối mạng! Bạn có thể gửi lại để tiếp tục từ vị trí đã dừng.");
        } catch (Exception e) {
            task.setStatus(FileTransferTask.Status.FAILED);
            listener.onStatusLog("Lỗi đọc/gửi file: " + e.getMessage(), true);
            listener.onFileCompleted(task, false, "Lỗi: " + e.getMessage());
        }
    }

    // ==========================================
    // Voice Call Signaling Public Methods
    // ==========================================

    public void sendCallOffer(int localVoiceUdpPort) {
        if (!isConnected()) return;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.CALL_OFFER);
                dos.writeInt(localVoiceUdpPort);
                dos.flush();
            }
        } catch (IOException e) {
            listener.onStatusLog("Lỗi gửi yêu cầu gọi thoại: " + e.getMessage(), true);
        }
    }

    public void sendCallAccept(int localVoiceUdpPort) {
        if (!isConnected()) return;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.CALL_ACCEPT);
                dos.writeInt(localVoiceUdpPort);
                dos.flush();
            }
        } catch (IOException ignored) {}
    }

    public void sendCallReject(String reason) {
        if (!isConnected()) return;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.CALL_REJECT);
                dos.writeUTF(reason != null ? reason : "Cuộc gọi bị từ chối");
                dos.flush();
            }
        } catch (IOException ignored) {}
    }

    public void sendCallHangup() {
        if (!isConnected()) return;
        try {
            synchronized (sendLock) {
                dos.writeInt(PacketType.CALL_HANGUP);
                dos.flush();
            }
        } catch (IOException ignored) {}
    }

    /**
     * Close connection gracefully.
     */
    public void close() {
        if (!running && socket.isClosed()) return;
        running = false;
        peer.setConnected(false);

        try {
            if (dos != null && !socket.isClosed()) {
                synchronized (sendLock) {
                    dos.writeInt(PacketType.DISCONNECT);
                    dos.flush();
                }
            }
        } catch (Exception ignored) {}

        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }
    }
}
