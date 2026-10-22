package com.lan.chat.voice;

import com.lan.chat.model.Peer;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Direct real-time Voice Chat service via UDP streaming.
 * Uses javax.sound.sampled:
 * - TargetDataLine for recording from Microphone
 * - SourceDataLine for playback on Speakers/Headphones
 * - DatagramSocket for low-latency UDP packet transmission
 */
public class VoiceCallService {

    public enum CallState {
        IDLE,
        CALLING,    // Outgoing ring
        RINGING,    // Incoming ring
        CONNECTED   // In call
    }

    private final int localUdpPort;
    private DatagramSocket udpSocket;

    private CallState state = CallState.IDLE;
    private Peer currentCallPeer;
    private int remoteVoicePort;
    private volatile boolean muted = false;

    // Audio lines
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private final AudioFormat audioFormat;

    // Threads
    private Thread audioSenderThread;
    private Thread audioReceiverThread;

    // Callbacks
    private Consumer<CallState> onStateChanged;
    private BiConsumer<Peer, Integer> onIncomingCall; // peer, remotePort
    private Consumer<String> onCallLog;

    public VoiceCallService(int preferredPort) {
        // High clarity, low bandwidth voice format: 16kHz, 16-bit, Mono, Little-Endian
        this.audioFormat = new AudioFormat(16000.0f, 16, 1, true, false);
        this.localUdpPort = initUdpSocket(preferredPort);
    }

    private int initUdpSocket(int preferredPort) {
        int port = preferredPort;
        while (port < preferredPort + 50) {
            try {
                udpSocket = new DatagramSocket(port);
                udpSocket.setReuseAddress(true);
                return port;
            } catch (SocketException e) {
                port++;
            }
        }
        try {
            udpSocket = new DatagramSocket(); // Any ephemeral port
            return udpSocket.getLocalPort();
        } catch (SocketException e) {
            throw new RuntimeException("Không thể mở UDP Socket cho Voice Chat: " + e.getMessage());
        }
    }

    public int getLocalUdpPort() {
        return localUdpPort;
    }

    public CallState getState() {
        return state;
    }

    public Peer getCurrentCallPeer() {
        return currentCallPeer;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (onCallLog != null) {
            onCallLog.accept(muted ? "🎙️ Đã tắt Micro." : "🎙️ Đã bật Micro.");
        }
    }

    public void setCallbacks(Consumer<CallState> onStateChanged,
                             BiConsumer<Peer, Integer> onIncomingCall,
                             Consumer<String> onCallLog) {
        this.onStateChanged = onStateChanged;
        this.onIncomingCall = onIncomingCall;
        this.onCallLog = onCallLog;
    }

    /**
     * Start local UDP receiver thread to be ready for incoming voice streams.
     */
    public synchronized void start() {
        if (audioReceiverThread != null && audioReceiverThread.isAlive()) return;

        audioReceiverThread = new Thread(this::receiveAudioLoop, "Voice-UdpReceiver");
        audioReceiverThread.setDaemon(true);
        audioReceiverThread.start();
    }

    /**
     * Call peer: marks state as CALLING.
     */
    public synchronized boolean initiateCall(Peer peer) {
        if (state != CallState.IDLE) return false;

        this.currentCallPeer = peer;
        this.remoteVoicePort = peer.getVoiceUdpPort();
        setState(CallState.CALLING);
        if (onCallLog != null) onCallLog.accept("📞 Đang gọi thoại cho " + peer.getNickname() + "...");
        return true;
    }

    /**
     * Notify an incoming call from remote peer.
     */
    public synchronized void handleIncomingOffer(Peer peer, int remotePort) {
        if (state != CallState.IDLE) return; // Busy

        this.currentCallPeer = peer;
        this.remoteVoicePort = remotePort;
        setState(CallState.RINGING);
        if (onIncomingCall != null) {
            onIncomingCall.accept(peer, remotePort);
        }
    }

    /**
     * Accept call and begin two-way audio streaming.
     */
    public synchronized void acceptCall(Peer peer, int remotePort) {
        this.currentCallPeer = peer;
        this.remoteVoicePort = remotePort;

        if (startAudioHardware()) {
            setState(CallState.CONNECTED);
            startAudioSending();
            if (onCallLog != null) onCallLog.accept("🟢 Cuộc gọi thoại đã kết nối với " + peer.getNickname() + "!");
        } else {
            hangup("Không thể mở thiết bị Micro/Loa!");
        }
    }

    /**
     * Peer accepted our call -> begin audio streaming.
     */
    public synchronized void onPeerAccepted(int remotePort) {
        this.remoteVoicePort = remotePort;
        if (startAudioHardware()) {
            setState(CallState.CONNECTED);
            startAudioSending();
            if (onCallLog != null) onCallLog.accept("🟢 " + currentCallPeer.getNickname() + " đã bắt máy! Cuộc gọi thoại bắt đầu.");
        } else {
            hangup("Lỗi phần cứng âm thanh!");
        }
    }

    /**
     * Hangup call or handle peer rejection.
     */
    public synchronized void hangup(String reason) {
        stopAudioSending();
        closeAudioHardware();

        Peer p = currentCallPeer;
        this.currentCallPeer = null;
        setState(CallState.IDLE);

        if (onCallLog != null) {
            onCallLog.accept("🔴 Cuộc gọi kết thúc" + (reason != null ? " (" + reason + ")" : ""));
        }
    }

    private void setState(CallState newState) {
        this.state = newState;
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }

    // ==========================================
    // Audio Hardware Management
    // ==========================================

    private boolean startAudioHardware() {
        try {
            // Setup Microphone (TargetDataLine)
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (AudioSystem.isLineSupported(micInfo)) {
                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                microphone.open(audioFormat);
                microphone.start();
            } else {
                System.err.println("Microphone line not supported");
            }

            // Setup Speaker (SourceDataLine)
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
            if (AudioSystem.isLineSupported(speakerInfo)) {
                speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                speaker.open(audioFormat);
                speaker.start();
            } else {
                System.err.println("Speaker line not supported");
            }

            return true;
        } catch (Exception e) {
            System.err.println("Lỗi mở thiết bị âm thanh: " + e.getMessage());
            closeAudioHardware();
            return false;
        }
    }

    private void closeAudioHardware() {
        if (microphone != null) {
            try {
                microphone.stop();
                microphone.close();
            } catch (Exception ignored) {}
            microphone = null;
        }
        if (speaker != null) {
            try {
                speaker.stop();
                speaker.close();
            } catch (Exception ignored) {}
            speaker = null;
        }
    }

    // ==========================================
    // Real-Time UDP Audio Streaming
    // ==========================================

    private void startAudioSending() {
        if (audioSenderThread != null && audioSenderThread.isAlive()) {
            audioSenderThread.interrupt();
        }

        audioSenderThread = new Thread(this::sendAudioLoop, "Voice-UdpSender");
        audioSenderThread.setDaemon(true);
        audioSenderThread.start();
    }

    private void stopAudioSending() {
        if (audioSenderThread != null) {
            audioSenderThread.interrupt();
            audioSenderThread = null;
        }
    }

    private void sendAudioLoop() {
        byte[] buffer = new byte[1024]; // 1024 bytes = 32ms audio frame
        try {
            InetAddress targetAddr = InetAddress.getByName(currentCallPeer.getIp());

            while (state == CallState.CONNECTED && microphone != null) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && !muted) {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, targetAddr, remoteVoicePort);
                    udpSocket.send(packet);
                }
            }
        } catch (Exception e) {
            if (state == CallState.CONNECTED) {
                System.err.println("Lỗi gửi gói tin âm thanh UDP: " + e.getMessage());
            }
        }
    }

    private void receiveAudioLoop() {
        byte[] buffer = new byte[2048];
        while (!udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                // If in call and speaker is open, playback the audio packet!
                if (state == CallState.CONNECTED && speaker != null) {
                    speaker.write(packet.getData(), 0, packet.getLength());
                }
            } catch (SocketException se) {
                break;
            } catch (Exception e) {
                if (state == CallState.CONNECTED) {
                    System.err.println("Lỗi nhận audio UDP: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        hangup("Dịch vụ thoại dừng hoạt động");
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (audioReceiverThread != null) audioReceiverThread.interrupt();
    }
}
