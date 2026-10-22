package com.lan.chat.ui.components;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.util.NetworkUtil;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class FileTransferBar extends JPanel {

    private final JLabel lblFileName;
    private final JLabel lblStats;
    private final JProgressBar progressBar;
    private final JButton btnPauseResume;
    private final JButton btnCancel;

    private FileTransferTask currentTask;
    private Consumer<FileTransferTask> onPauseResumeAction;
    private Consumer<FileTransferTask> onCancelAction;

    public FileTransferBar() {
        setLayout(new BorderLayout(10, 6));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        setBackground(Color.WHITE);
        setVisible(false);

        // Top line: File name and stats
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        lblFileName = new JLabel("Truyền file");
        lblFileName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFileName.setForeground(new Color(15, 23, 42));

        lblStats = new JLabel("0% (0 KB / 0 KB) - 0 KB/s");
        lblStats.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStats.setForeground(new Color(100, 116, 139));

        topPanel.add(lblFileName, BorderLayout.WEST);
        topPanel.add(lblStats, BorderLayout.EAST);

        // Bottom line: Progress bar, Pause/Resume, and Cancel buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setOpaque(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setForeground(new Color(79, 70, 229));
        progressBar.setBackground(new Color(241, 245, 249));

        JPanel buttonBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonBox.setOpaque(false);

        btnPauseResume = new JButton("Tạm dừng");
        btnPauseResume.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnPauseResume.setFocusPainted(false);
        btnPauseResume.setMargin(new Insets(2, 8, 2, 8));
        btnPauseResume.addActionListener(e -> {
            if (currentTask != null && onPauseResumeAction != null) {
                onPauseResumeAction.accept(currentTask);
            }
        });

        btnCancel = new JButton("Hủy");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnCancel.setForeground(new Color(220, 38, 38));
        btnCancel.setFocusPainted(false);
        btnCancel.setMargin(new Insets(2, 8, 2, 8));
        btnCancel.addActionListener(e -> {
            if (currentTask != null && onCancelAction != null) {
                onCancelAction.accept(currentTask);
            }
        });

        buttonBox.add(btnPauseResume);
        buttonBox.add(btnCancel);

        bottomPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(buttonBox, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setOnPauseResumeAction(Consumer<FileTransferTask> onPauseResumeAction) {
        this.onPauseResumeAction = onPauseResumeAction;
    }

    public void setOnCancelAction(Consumer<FileTransferTask> onCancelAction) {
        this.onCancelAction = onCancelAction;
    }

    public void updateTask(FileTransferTask task) {
        this.currentTask = task;
        if (task == null) {
            setVisible(false);
            return;
        }

        setVisible(true);
        String prefix = task.isUpload() ? "[GỬI] " : "[NHẬN] ";
        lblFileName.setText(prefix + task.getFileName() + " (Chunking 8KB & Resume)");

        int percent = task.getProgressPercent();
        progressBar.setValue(percent);
        progressBar.setString(percent + "%");

        String transferred = NetworkUtil.formatBytes(task.getTransferredBytes());
        String total = NetworkUtil.formatBytes(task.getTotalBytes());
        String speed = NetworkUtil.formatSpeed((long) task.getCurrentSpeedBps());

        long eta = task.getEstimatedRemainingSeconds();
        String etaStr = (eta > 0) ? (" - Còn " + formatEta(eta)) : "";

        if (task.getStatus() == FileTransferTask.Status.PAUSED) {
            btnPauseResume.setText("Tiếp tục");
            lblStats.setText(String.format("Tạm dừng: %s / %s (%d%%)", transferred, total, percent));
        } else {
            btnPauseResume.setText("Tạm dừng");
            lblStats.setText(String.format("%s / %s (%s%s)", transferred, total, speed, etaStr));
        }
    }

    private String formatEta(long seconds) {
        if (seconds < 60) return seconds + "s";
        long mins = seconds / 60;
        long remSec = seconds % 60;
        return mins + "m " + remSec + "s";
    }

    public void transferFinished(boolean success, String message) {
        if (success) {
            progressBar.setValue(100);
            progressBar.setString("100% - Hoàn tất!");
            lblStats.setText("Đã hoàn tất");
            Timer timer = new Timer(3000, e -> setVisible(false));
            timer.setRepeats(false);
            timer.start();
        } else {
            lblStats.setText("Lỗi: " + message);
            Timer timer = new Timer(5000, e -> setVisible(false));
            timer.setRepeats(false);
            timer.start();
        }
    }
}
