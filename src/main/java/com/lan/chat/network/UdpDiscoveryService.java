package com.lan.chat.network;

import com.lan.chat.model.Peer;
import com.lan.chat.util.NetworkUtil;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * UDP Auto-Discovery Service robustly tuned for Multi-Machine LAN operation.
 * - Broadcasts using:
 *   1. Direct physical subnet broadcasts (e.g. 192.168.1.255, 10.x.x.255) per interface.
 *   2. Global broadcast (255.255.255.255).
 *   3. Multicast group (239.255.10.10:8888).
 *   4. Direct unicast ACK replies.
 */
public class UdpDiscoveryService {

    private static final String MULTICAST_GROUP_IP = "239.255.10.10";
    private static final int DISCOVERY_PORT = 8888;

    private static final String PACKET_DISCOVER = "LAN_CHAT_DISCOVER";
    private static final String PACKET_ACK = "LAN_CHAT_DISCOVER_ACK";
    private static final String PACKET_GOODBYE = "LAN_CHAT_GOODBYE";

    private final String instanceId;
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
        this.instanceId = UUID.randomUUID().toString();
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
            socket.setBroadcast(true); // Crucial for broadcast transmission without permission error!
            socket.setTimeToLive(16);

            // Join multicast group across all multicast-capable interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isUp() && !ni.isLoopback() && ni.supportsMulticast()) {
                    try {
                        socket.joinGroup(new InetSocketAddress(groupAddress, DISCOVERY_PORT), ni);
                    } catch (Exception ignored) {}
                }
            }

            // Start background receiver listener thread
            listenerThread = new Thread(this::listenLoop, "UdpDiscovery-Listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Start periodic beacon thread (beacons every 2 seconds)
            beaconThread = new Thread(this::beaconLoop, "UdpDiscovery-Beacon");
            beaconThread.setDaemon(true);
            beaconThread.start();

            // Broadcast initial beacon immediately
            broadcastBeacon();

        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo UDP Discovery: " + e.getMessage());
        }
    }

    private void beaconLoop() {
        while (running) {
            try {
                broadcastBeacon();
                Thread.sleep(2000); // Send beacon every 2s
            } catch (InterruptedException e) {
                break;
            } catch (Exception ignored) {}
        }
    }

    /**
     * Broadcasts discovery beacon across all network interfaces, subnets, and multicast group.
     */
    public void broadcastBeacon() {
        if (!running) return;
        try {
            // Syntax: LAN_CHAT_DISCOVER|instanceId|nickname|tcpPort|voiceUdpPort
            String payload = String.format("%s|%s|%s|%d|%d", PACKET_DISCOVER, instanceId, nickname, tcpPort, voiceUdpPort);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);

            // 1. Broadcast specifically out of each active network interface
            List<NetworkUtil.BroadcastTarget> targets = NetworkUtil.getBroadcastTargets();
            for (NetworkUtil.BroadcastTarget target : targets) {
                try (DatagramSocket senderSock = new DatagramSocket(new InetSocketAddress(target.localAddress(), 0))) {
                    senderSock.setBroadcast(true);
                    DatagramPacket bPacket = new DatagramPacket(data, data.length, target.broadcastAddress(), DISCOVERY_PORT);
                    senderSock.send(bPacket);
                } catch (Exception ignored) {}
            }

            // 2. Global broadcast fallback (255.255.255.255)
            if (socket != null && !socket.isClosed()) {
                try {
                    InetAddress globalBcast = InetAddress.getByName("255.255.255.255");
                    DatagramPacket gPacket = new DatagramPacket(data, data.length, globalBcast, DISCOVERY_PORT);
                    socket.send(gPacket);
                } catch (Exception ignored) {}
            }

            // 3. Multicast group
            if (socket != null && !socket.isClosed() && groupAddress != null) {
                try {
                    DatagramPacket mPacket = new DatagramPacket(data, data.length, groupAddress, DISCOVERY_PORT);
                    socket.send(mPacket);
                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
    }

    private void sendUnicastAck(InetAddress targetAddress, int targetPort) {
        try {
            String payload = String.format("%s|%s|%s|%d|%d", PACKET_ACK, instanceId, nickname, tcpPort, voiceUdpPort);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            try (DatagramSocket directSock = new DatagramSocket()) {
                DatagramPacket p = new DatagramPacket(data, data.length, targetAddress, targetPort);
                directSock.send(p);
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
                InetAddress senderAddress = packet.getAddress();
                String senderIp = senderAddress.getHostAddress();

                parseAndDispatch(msg, senderIp, senderAddress);

            } catch (SocketException se) {
                break; // Socket closed
            } catch (Exception e) {
                if (running) {
                    System.err.println("Lỗi UDP packet receive: " + e.getMessage());
                }
            }
        }
    }

    private void parseAndDispatch(String msg, String senderIp, InetAddress senderAddress) {
        String[] parts = msg.split("\\|");
        if (parts.length < 5) return;

        String header = parts[0];
        String senderId = parts[1];
        String peerNick = parts[2];
        int peerTcpPort;
        int peerVoicePort;

        try {
            peerTcpPort = Integer.parseInt(parts[3]);
            peerVoicePort = Integer.parseInt(parts[4]);
        } catch (NumberFormatException e) {
            return;
        }

        // Drop packets originating from our own process instance
        if (this.instanceId.equals(senderId)) {
            return;
        }

        Peer peer = new Peer(senderIp, peerTcpPort, peerNick);
        peer.setVoiceUdpPort(peerVoicePort);
        peer.setAutoDiscovered(true);
        peer.setOnline(true);

        if (PACKET_DISCOVER.equals(header)) {
            // Reply with direct Unicast ACK so both machines discover each other instantly
            sendUnicastAck(senderAddress, DISCOVERY_PORT);

            if (onPeerDiscovered != null) {
                onPeerDiscovered.accept(peer);
            }
        } else if (PACKET_ACK.equals(header)) {
            if (onPeerDiscovered != null) {
                onPeerDiscovered.accept(peer);
            }
        } else if (PACKET_GOODBYE.equals(header)) {
            if (onPeerLeft != null) {
                onPeerLeft.accept(peer);
            }
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed() && groupAddress != null) {
                String goodbye = String.format("%s|%s|%s|%d|%d", PACKET_GOODBYE, instanceId, nickname, tcpPort, voiceUdpPort);
                byte[] data = goodbye.getBytes(StandardCharsets.UTF_8);
                DatagramPacket p = new DatagramPacket(data, data.length, groupAddress, DISCOVERY_PORT);
                socket.send(p);
            }
        } catch (Exception ignored) {}

        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }

        if (listenerThread != null) listenerThread.interrupt();
        if (beaconThread != null) beaconThread.interrupt();
    }
}
