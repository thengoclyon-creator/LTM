package com.lan.chat.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class NetworkUtil {

    public record BroadcastTarget(InetAddress localAddress, InetAddress broadcastAddress) {}

    /**
     * Get all active IPv4 LAN addresses of this machine.
     * Prioritizes real physical Wi-Fi and Ethernet adapters,
     * deprioritizing virtual (VirtualBox, VMware, WSL, Hamachi) adapters.
     */
    public static List<String> getLocalIPv4Addresses() {
        List<String> physicalIps = new ArrayList<>();
        List<String> otherLanIps = new ArrayList<>();
        List<String> virtualIps = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                String desc = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
                boolean isVirtual = desc.contains("virtual") || desc.contains("vmware") || desc.contains("vbox")
                        || desc.contains("vethernet") || desc.contains("wsl") || desc.contains("bluetooth")
                        || desc.contains("pseudo") || desc.contains("hamachi") || desc.contains("zerotier")
                        || desc.contains("tailscale") || desc.contains("npcap") || desc.contains("hyper-v");

                boolean isPhysicalWifiOrEth = desc.contains("wi-fi") || desc.contains("wifi") || desc.contains("wireless")
                        || desc.contains("wlan") || desc.contains("ethernet") || desc.contains("eth");

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("169.254.")) {
                            if (isVirtual) {
                                virtualIps.add(ip);
                            } else if (isPhysicalWifiOrEth) {
                                physicalIps.add(ip);
                            } else {
                                otherLanIps.add(ip);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<String> result = new ArrayList<>();
        result.addAll(physicalIps);
        result.addAll(otherLanIps);
        result.addAll(virtualIps);

        if (result.isEmpty()) {
            result.add("127.0.0.1");
        }
        return result;
    }

    /**
     * Get primary LAN IP address (guaranteed to be physical Wi-Fi/Ethernet if available).
     */
    public static String getPrimaryLocalIP() {
        List<String> ips = getLocalIPv4Addresses();
        return ips.isEmpty() ? "127.0.0.1" : ips.get(0);
    }

    /**
     * Collects all active subnet broadcast targets across all non-loopback network interfaces.
     */
    public static List<BroadcastTarget> getBroadcastTargets() {
        List<BroadcastTarget> targets = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;

                for (InterfaceAddress ifAddr : ni.getInterfaceAddresses()) {
                    InetAddress local = ifAddr.getAddress();
                    InetAddress broadcast = ifAddr.getBroadcast();
                    if (local instanceof Inet4Address && broadcast != null) {
                        targets.add(new BroadcastTarget(local, broadcast));
                    }
                }
            }
        } catch (Exception ignored) {}
        return targets;
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
