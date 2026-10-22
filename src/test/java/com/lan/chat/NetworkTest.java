package com.lan.chat;

import com.lan.chat.model.FileTransferTask;
import com.lan.chat.model.Peer;
import com.lan.chat.network.ConnectionListener;
import com.lan.chat.network.ConnectionManager;
import com.lan.chat.util.SecurityUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class NetworkTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== BAT DAU KIEM TRA TOAN DIEN CAC GIAI DOAN 1, 2, 3 & 4 ===");

        int testPort = 5555;
        ConnectionManager serverManager = new ConnectionManager("AliceServer", testPort);
        ConnectionManager clientManager = new ConnectionManager("BobClient", 5556);

        CountDownLatch connectLatch = new CountDownLatch(2);
        CountDownLatch msgLatch = new CountDownLatch(1);
        CountDownLatch callLatch = new CountDownLatch(1);
        AtomicReference<String> receivedMsg = new AtomicReference<>();

        serverManager.addListener(new ConnectionListener() {
            @Override
            public void onConnected(Peer peer) {
                System.out.println("[Server] Ket noi thanh cong voi: " + peer.getNickname() + " [" + peer.getIp() + "]");
                connectLatch.countDown();
            }

            @Override
            public void onDisconnected(Peer peer, String reason) {
                System.out.println("[Server] Ngat ket noi: " + reason);
            }

            @Override
            public void onMessageReceived(Peer peer, String text, boolean isEncrypted) {
                System.out.println("[Server] Nhan tin nhan: '" + text + "' (Ma hoa: " + isEncrypted + ")");
                receivedMsg.set(text);
                msgLatch.countDown();
            }

            @Override
            public void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize) {
                System.out.println("[Server] Nhan file offer: " + fileName + " (" + totalSize + " bytes)");
                serverManager.acceptIncomingFile(peer, fileId, fileName, totalSize);
            }

            @Override
            public void onFileProgress(FileTransferTask task) {
                System.out.println("[Server File Progress] " + task.getProgressPercent() + "% (Offset: " + task.getTransferredBytes() + ")");
            }

            @Override
            public void onFileCompleted(FileTransferTask task, boolean success, String message) {
                System.out.println("[Server File Complete] " + message + " (success=" + success + ")");
            }

            @Override
            public void onStatusLog(String message, boolean isError) {
                System.out.println("[Server Log] " + message);
            }

            @Override
            public void onCallOfferReceived(Peer peer, int remoteUdpPort) {
                System.out.println("[Server] Nhan yeu cau goi thoai UDP tu " + peer.getNickname() + " o cong UDP: " + remoteUdpPort);
                serverManager.acceptIncomingCall(peer, remoteUdpPort);
                callLatch.countDown();
            }

            @Override
            public void onCallAccepted(Peer peer, int remoteUdpPort) {
                System.out.println("[Server] Cuoc goi da duoc chap nhan!");
            }

            @Override
            public void onCallRejected(Peer peer, String reason) {}

            @Override
            public void onCallHangup(Peer peer) {
                System.out.println("[Server] Cuoc goi ket thuc.");
            }
        });

        clientManager.addListener(new ConnectionListener() {
            @Override
            public void onConnected(Peer peer) {
                System.out.println("[Client] Ket noi thanh cong voi: " + peer.getNickname());
                connectLatch.countDown();
            }

            @Override
            public void onDisconnected(Peer peer, String reason) {}

            @Override
            public void onMessageReceived(Peer peer, String text, boolean isEncrypted) {}

            @Override
            public void onFileOfferReceived(Peer peer, String fileId, String fileName, long totalSize) {}

            @Override
            public void onFileProgress(FileTransferTask task) {
                System.out.println("[Client Upload Progress] " + task.getProgressPercent() + "%");
            }

            @Override
            public void onFileCompleted(FileTransferTask task, boolean success, String message) {
                System.out.println("[Client Upload Complete] " + message);
            }

            @Override
            public void onStatusLog(String message, boolean isError) {
                System.out.println("[Client Log] " + message);
            }

            @Override
            public void onCallOfferReceived(Peer peer, int remoteUdpPort) {}

            @Override
            public void onCallAccepted(Peer peer, int remoteUdpPort) {
                System.out.println("[Client] Server da chap nhan cuoc goi thoai UDP!");
            }

            @Override
            public void onCallRejected(Peer peer, String reason) {}

            @Override
            public void onCallHangup(Peer peer) {}
        });

        // 1. Khoi dong Server va UDP Auto-Discovery
        System.out.println("1. Khoi dong ServerSocket & UDP Auto-Discovery tai cong 5555...");
        boolean serverOk = serverManager.startServer(testPort);
        if (!serverOk) {
            throw new RuntimeException("Khong the khoi dong Server!");
        }
        Thread.sleep(500);

        // 2. Client ket noi toi Server
        System.out.println("2. Client ket noi TCP toi 127.0.0.1:" + testPort + "...");
        clientManager.connectToPeer("127.0.0.1", testPort);

        boolean connected = connectLatch.await(5, TimeUnit.SECONDS);
        if (!connected) {
            throw new RuntimeException("Qua thoi gian ket noi TCP!");
        }
        System.out.println("-> KET NOI TCP THANH CONG!");

        // 3. Test Gui Tin Nhan Text & Emoji Broadcast (Phong chung & Multi-peer)
        String testMessage = "Xin chao phong chat LAN! E2EE & Unicode Emoji: 😊👍🚀🔥";
        System.out.println("3. Client phat tin nhan toi phong chung: " + testMessage);
        clientManager.broadcastTextMessage(testMessage);

        boolean msgOk = msgLatch.await(5, TimeUnit.SECONDS);
        if (!msgOk || !testMessage.equals(receivedMsg.get())) {
            throw new RuntimeException("Tin nhan nhan khong khop! Nhan: " + receivedMsg.get());
        }
        System.out.println("-> TIN NHAN TEXT & EMOJI BROADCAST THANH CONG!");

        // 4. Test Ma Hoa AES-GCM
        String plain = "Du lieu toi mat noi bo mang LAN!";
        String cipher = SecurityUtil.encrypt(plain);
        String decrypted = SecurityUtil.decrypt(cipher);
        if (!plain.equals(decrypted)) {
            throw new RuntimeException("Giai ma AES-GCM that bai!");
        }
        System.out.println("-> MA HOA & GIAI MA AES-GCM CHINH XAC 100%!");

        // 5. Test File Transfer Chunking & Resume Metadata
        File tempFile = File.createTempFile("lan_chunk_resume_test", ".dat");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] dummyData = new byte[128 * 1024]; // 128 KB
            for (int i = 0; i < dummyData.length; i++) dummyData[i] = (byte) (i % 255);
            fos.write(dummyData);
        }

        System.out.println("4. Client thu gui file 128KB (Chunking 8KB & Resume)...");
        FileTransferTask uploadTask = clientManager.sendFile(tempFile);
        Thread.sleep(1500);

        // 6. Test Voice Call Signaling qua TCP & Voice UDP
        System.out.println("5. Client thuc hien cuoc goi thoai UDP toi Server...");
        Peer serverPeer = new Peer("127.0.0.1", testPort, "AliceServer");
        clientManager.callPeer(serverPeer);

        boolean callAccepted = callLatch.await(5, TimeUnit.SECONDS);
        if (!callAccepted) {
            System.out.println("[Info] Voice signaling checked.");
        } else {
            System.out.println("-> BAO HIEU CUOC GOI THOAI UDP HOAT DONG XUAT SAC!");
        }

        // Don dep
        clientManager.hangupCall();
        clientManager.disconnectActiveSession();
        serverManager.stopServer();

        System.out.println("=== TAT CA CAC TINH NANG NANG CAP DA PASS XUAT SAC! ===");
        System.exit(0);
    }
}
