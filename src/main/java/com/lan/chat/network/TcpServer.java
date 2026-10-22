package com.lan.chat.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.function.Consumer;

/**
 * ServerSocket listener running on background daemon thread.
 */
public class TcpServer {

    private final int port;
    private final Consumer<Socket> onClientAccepted;
    private final Consumer<String> onStatus;

    private ServerSocket serverSocket;
    private volatile boolean listening = false;
    private Thread serverThread;

    public TcpServer(int port, Consumer<Socket> onClientAccepted, Consumer<String> onStatus) {
        this.port = port;
        this.onClientAccepted = onClientAccepted;
        this.onStatus = onStatus;
    }

    public TcpServer(ServerSocket serverSocket, Consumer<Socket> onClientAccepted, Consumer<String> onStatus) {
        this.serverSocket = serverSocket;
        this.port = serverSocket != null ? serverSocket.getLocalPort() : 5000;
        this.onClientAccepted = onClientAccepted;
        this.onStatus = onStatus;
    }

    public synchronized void start() throws IOException {
        if (listening) return;

        if (serverSocket == null || serverSocket.isClosed()) {
            serverSocket = new ServerSocket(port);
        }
        listening = true;

        serverThread = new Thread(this::listenLoop, "TcpServerThread-Port-" + port);
        serverThread.setDaemon(true);
        serverThread.start();

        onStatus.accept("ServerSocket đang lắng nghe tại cổng " + port + " (Sẵn sàng nhận kết nối).");
    }

    private void listenLoop() {
        while (listening && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                // Socket configuration
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                onStatus.accept("Phát hiện kết nối đến từ: " + socket.getRemoteSocketAddress());
                if (onClientAccepted != null) {
                    onClientAccepted.accept(socket);
                }
            } catch (SocketException se) {
                if (!listening) {
                    break; // Server was intentionally stopped
                }
                onStatus.accept("Lỗi ServerSocket: " + se.getMessage());
            } catch (IOException e) {
                if (listening) {
                    onStatus.accept("Lỗi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        listening = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
        if (serverThread != null) {
            serverThread.interrupt();
        }
        onStatus.accept("Đã dừng ServerSocket tại cổng " + port);
    }

    public boolean isListening() {
        return listening && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return port;
    }
}
