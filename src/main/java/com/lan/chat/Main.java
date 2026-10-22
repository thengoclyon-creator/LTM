package com.lan.chat;

import com.lan.chat.network.ConnectionManager;
import com.lan.chat.ui.MainChatFrame;

import javax.swing.*;
import java.net.InetAddress;

public class Main {

    public static void main(String[] args) {
        // Setup Modern Look and Feel
        try {
            // Attempt FlatLaf Dark Theme
            Class<?> flatDarkLaf = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            flatDarkLaf.getMethod("setup").invoke(null);
        } catch (Exception e) {
            try {
                // Fallback to system Look and Feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        // Determine default user nickname from OS or hostname
        String defaultNickname = System.getProperty("user.name", "User");
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isEmpty()) {
                defaultNickname += "@" + hostName;
            }
        } catch (Exception ignored) {}

        if (args.length > 0 && !args[0].trim().isEmpty()) {
            defaultNickname = args[0].trim();
        }

        int defaultPort = 5000;
        if (args.length > 1) {
            try {
                defaultPort = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        final String finalNickname = defaultNickname;
        final int finalPort = defaultPort;

        // Launch UI on Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ConnectionManager manager = new ConnectionManager(finalNickname, finalPort);
            MainChatFrame frame = new MainChatFrame(manager);
            frame.setTitle("LAN Chat Pro - [" + finalNickname + " : Port " + finalPort + "]");
            frame.setVisible(true);
        });
    }
}
