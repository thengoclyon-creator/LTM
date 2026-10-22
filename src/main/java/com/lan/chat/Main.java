package com.lan.chat;

import com.lan.chat.network.ConnectionManager;
import com.lan.chat.ui.MainChatFrame;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

public class Main {

    public static void main(String[] args) {
        // Setup Modern Clean Light Theme
        try {
            Class<?> flatLightLaf = Class.forName("com.formdev.flatlaf.FlatLightLaf");
            flatLightLaf.getMethod("setup").invoke(null);

            // Configure modern rounded corners and clean padding
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("ScrollBar.showButtons", false);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.thumbArc", 8);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        // Global font smoothing and Segoe UI base
        Font appFont = new Font("Segoe UI", Font.PLAIN, 13);
        setUIFont(new javax.swing.plaf.FontUIResource(appFont));

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

    private static void setUIFont(javax.swing.plaf.FontUIResource f) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }
}
