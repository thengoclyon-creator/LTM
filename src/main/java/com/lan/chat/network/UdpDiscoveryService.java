package com.lan.chat.network;

import com.lan.chat.model.Peer;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.function.Consumer;

/**
 * UDP Auto-Discovery Service for LAN.
 * Periodically broadcasts presence beacons so nodes automatically find each other
 * without typing IP addresses.
 * Uses Multicast (239.255.10.10:8888) with fallback to Subnet Broadcast,
 * allowing discovery across different PCs in LAN as well as multiple instances on the same machine!
 */
public class UdpDiscoveryService {

    private static final String MULTICAST_GROUP_IP = "239.255.10.10";
    private static final int DISCOVERY_PORT = 8888;
    private static final String PACKET_HEADER = "LAN_CHAT_DISCOVER";
    private static final String GOODBYE_HEADER = "LAN_CHAT_GOODBYE";

    private final String nickname;
    private final int tcpPort;
    private final int voiceUdpPort;
    private final Consumer<Peer> onPeerDiscovered;
    private final Consumer<Peer> onPeerLeft;

    private MulticastSocket socket;
    private InetAddress groupAddress;
    private volatile boolean running = false;
    private Thread listenerThread;
    private Thread beaconThread;

    public UdpDiscoveryService(String nickname, int tcpPort, int voiceUdpPort,
                               Consumer<Peer> onPeerDiscovered, Consumer<Peer> onPeerLeft) {
        this.nickname = nickname;
        this.tcpPort = tcpPort;
        this.voiceUdpPort = voiceUdpPort;
        this.onPeerDiscovered = onPeerDiscovered;
        this.onPeerLeft = onPeerLeft;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        try {
            groupAddress = InetAddress.getByName(MULTICAST_GROUP_IP);
            socket = new MulticastSocket(DISCOVERY_PORT);
            socket.setReuseAddress(true);
            socket.setTimeToLive(4);

            // Join multicast group on all network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    try {
                        socket.joinGroup(new InetSocketAddress(groupAddress, DISCOVERY_PORT), ni);
                    } catch (Exception ignored) {}
                }
            }

            // Also join default
            try {
                socket.joinGroup(groupAddress);
            } catch (Exception ignored) {}

            // Start listener thread
            listenerThread = new Thread(this::listenLoop, "UdpDiscovery-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Start periodic beacon broadcast thread
            beaconThread = new Thread(this::beaconLoop, "UdpDiscovery-Beacon");
            beaconThread.setDaemon(true);
            beaconThread.start();

            // Send immediate beacon
            broadcastBeacon();

        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo UDP Discovery: " + e.getMessage());
        }
    }

    private void beaconLoop() {
        while (running) {
            try {
                broadcastBeacon();
                Thread.sleep(3000); // Broadcast beacon every 3s
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // Ignore transient network errors
            }
        }
    }

    public void broadcastBeacon() {
        if (socket == null || socket.isClosed()) return;
        try {
            // Packet syntax: LAN_CHAT_DISCOVER|nickname|tcpPort|voiceUdpPort
            String payload = String.format("%s|%s|%d|%d", PACKET_HEADER, nickname, tcpPort, voiceUdpPort);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);

            // Send to Multicast Group
            DatagramPacket mPacket = new DatagramPacket(data, data.length, groupAddress, DISCOVERY_PORT);
            socket.send(mPacket);

            // Also broadcast to all subnet broadcast addresses as fallback
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ifAddr : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = ifAddr.getBroadcast();
                    if (broadcast != null) {
                        DatagramPacket bPacket = new DatagramPacket(data, data.length, broadcast, DISCOVERY_PORT);
                        socket.send(bPacket);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void listenLoop() {
        byte[] buffer = new byte[2048];
        while (running && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String senderIp = packet.getAddress().getHostAddress();

                parseAndDispatch(msg, senderIp);

            } catch (SocketException se) {
                break; // Socket closed
            } catch (Exception e) {
                if (running) {
                    System.err.println("Lỗi UDP packet receive: " + e.getMessage());
                }
            }
        }
    }

    private void parseAndDispatch(String msg, String senderIp) {
        String[] parts = msg.split("\\|");
        if (parts.length < 4) return;

        String header = parts[0];
        String peerNick = parts[1];
        int peerTcpPort;
        int peerVoicePort;

        try {
            peerTcpPort = Integer.parseInt(parts[2]);
            peerVoicePort = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }

        // Avoid discovering self on same IP and same TCP port
        if (peerTcpPort == this.tcpPort) {
            return;
        }

        Peer peer = new Peer(senderIp, peerTcpPort, peerNick);
        peer.setVoiceUdpPort(peerVoicePort);

        if (PACKET_HEADER.equals(header)) {
            if (onPeerDiscovered != null) {
                onPeerDiscovered.accept(peer);
            }
        } else if (GOODBYE_HEADER.equals(header)) {
            if (onPeerLeft != null) {
                onPeerLeft.accept(peer);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            // Send Goodbye beacon
            if (socket != null && !socket.isClosed() && groupAddress != null) {
                String goodbye = String.format("%s|%s|%d|%d", GOODBYE_HEADER, nickname, tcpPort, voiceUdpPort);
                byte[] data = goodbye.getBytes(StandardCharsets.UTF_8);
                DatagramPacket p = new DatagramPacket(data, data.length, groupAddress, DISCOVERY_PORT);
                socket.send(p);
            }
        } catch (Exception ignored) {}

        if (socket != null && !socket.isClosed()) {
            try {
                socket.leaveGroup(groupAddress);
            } catch (Exception ignored) {}
            socket.close();
        }

        if (listenerThread != null) listenerThread.interrupt();
        if (beaconThread != null) beaconThread.interrupt();
    }
}
