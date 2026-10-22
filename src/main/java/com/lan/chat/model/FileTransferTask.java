package com.lan.chat.model;

import java.io.*;

public class FileTransferTask {
    public enum Status {
        PENDING,
        TRANSFERRING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String fileId;
    private final String fileName;
    private final long totalBytes;
    private long transferredBytes;
    private final boolean isUpload;
    private final File localFile;
    private Status status;
    private long startTime;
    private long lastTime;
    private long lastBytes;
    private double currentSpeedBps;

    public FileTransferTask(String fileId, String fileName, long totalBytes, boolean isUpload, File localFile) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.totalBytes = totalBytes;
        this.transferredBytes = 0;
        this.isUpload = isUpload;
        this.localFile = localFile;
        this.status = Status.PENDING;
        this.startTime = System.currentTimeMillis();
        this.lastTime = startTime;
        this.lastBytes = 0;
        this.currentSpeedBps = 0;
    }

    public synchronized void updateProgress(long currentBytes) {
        this.transferredBytes = currentBytes;
        long now = System.currentTimeMillis();
        long timeDiff = now - lastTime;
        if (timeDiff >= 400) { // Update speed metrics every 400ms
            long bytesDiff = currentBytes - lastBytes;
            this.currentSpeedBps = (bytesDiff * 1000.0) / timeDiff;
            this.lastTime = now;
            this.lastBytes = currentBytes;
        }
    }

    public int getProgressPercent() {
        if (totalBytes <= 0) return 0;
        return (int) Math.min(100, (transferredBytes * 100) / totalBytes);
    }

    public long getEstimatedRemainingSeconds() {
        if (currentSpeedBps <= 0) return 0;
        long remainingBytes = Math.max(0, totalBytes - transferredBytes);
        return (long) (remainingBytes / currentSpeedBps);
    }

    // Part file for receiving
    public File getPartFile() {
        return new File(localFile.getAbsolutePath() + ".part");
    }

    // Metadata file for resume offset persistence
    public File getMetaFile() {
        return new File(localFile.getAbsolutePath() + ".meta");
    }

    public void saveResumeMetadata() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getMetaFile()))) {
            writer.write(fileId + "\n");
            writer.write(fileName + "\n");
            writer.write(totalBytes + "\n");
            writer.write(transferredBytes + "\n");
        } catch (IOException ignored) {}
    }

    public void cleanResumeFiles() {
        File meta = getMetaFile();
        if (meta.exists()) meta.delete();
    }

    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public long getTotalBytes() { return totalBytes; }
    public synchronized long getTransferredBytes() { return transferredBytes; }
    public boolean isUpload() { return isUpload; }
    public File getLocalFile() { return localFile; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public synchronized double getCurrentSpeedBps() { return currentSpeedBps; }
}
