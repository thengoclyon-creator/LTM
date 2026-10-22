package com.lan.chat.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtil {

    /**
     * Get all active IPv4 LAN addresses of this machine.
     * Filters out loopback and link-local (169.254.x.x) addresses,
     * prioritizing real Wi-Fi / Ethernet LAN IP ranges (192.168.x.x, 10.x.x.x, 172.x.x.x).
     */
    public static List<String> getLocalIPv4Addresses() {
        List<String> ipList = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("169.254.")) {
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                ipList.add(0, ip); // Prioritize LAN addresses
                            } else {
                                ipList.add(ip);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ipList.isEmpty()) {
            ipList.add("127.0.0.1");
        }
        return ipList;
    }

    /**
     * Get primary LAN IP address.
     */
    public static String getPrimaryLocalIP() {
        List<String> ips = getLocalIPv4Addresses();
        return ips.isEmpty() ? "127.0.0.1" : ips.get(0);
    }

    /**
     * Format bytes into readable string (KB, MB, GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Format transfer speed.
     */
    public static String formatSpeed(long bytesPerSec) {
        return formatBytes(bytesPerSec) + "/s";
    }
}
