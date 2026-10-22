# 🚀 LAN CHAT PRO - Hệ Thống Chat, Voice UDP & Truyền File Đa Thiết Bị (P2P Mesh)

Ứng dụng chat nội bộ mạng LAN hoàn chỉnh được viết bằng ngôn ngữ **Java**, kết hợp mô hình phân tán **P2P Mesh Network**, hỗ trợ đầy đủ 4 giai đoạn chuyên sâu:

---

## 🌟 TÍNH NĂNG NỔI BẬT ĐÃ HOÀN THIỆN

### 1. 🔄 Hệ Thống File Transfer Siêu Việt (Hỗ trợ GB & Resume Download)
- **Truyền file theo từng Chunk 8KB (`byte[] buffer = new byte[8192]`):** Cho phép gửi tệp dung lượng hàng GB (phim ảnh, file nén, ISO) mà bộ nhớ RAM chỉ tốn cố định vài Megabyte, không bao giờ bị tràn RAM (`OutOfMemoryError`).
- **Cơ chế Resume Download (Tự động nối file khi đứt cáp):**
  - Trong quá trình tải, dữ liệu được ghi vào file tạm `.part` (ví dụ: `Tailieu.zip.part`) bằng `RandomAccessFile`.
  - Tự động lưu tiến độ offset vào file `.meta`.
  - Nếu gặp lỗi `SocketException` (rút cáp, mất Wi-Fi), trạng thái chuyển sang `PAUSED`.
  - Khi có mạng lại hoặc bấm **`▶️ Tiếp tục (Resume)`**, bên nhận gửi `FILE_RESUME_REQ` kèm vị trí byte hiện có (`startOffset`). Bên gửi gọi `raf.seek(startOffset)` để chỉ gửi phần còn thiếu!
  - Khi đạt 100%, tự động xóa file `.meta` và đổi tên `.part` thành tên file gốc.

### 2. 📡 UDP Auto-Discovery (Tự Động Dò Tìm Thiết Bị Trong Mạng LAN)
- Sử dụng `MulticastSocket` nhóm `239.255.10.10:8888` kết hợp Subnet Broadcast (`255.255.255.255`).
- Định kỳ 3 giây phát một gói tin Beacon: `LAN_CHAT_DISCOVER|nickname|tcpPort|voicePort`.
- Các máy khác trong LAN bắt được gói tin sẽ tự động thêm vào danh sách **"🌐 Thiết bị Online"** với nhãn `🟢 Online (Tự động phát hiện)`.
- **Không ai phải gõ địa chỉ IP thủ công nữa!**
- Hoạt động mượt mà trên cả mạng LAN thật (nhiều máy khác nhau) lẫn khi mở nhiều cửa sổ trên cùng 1 máy tính!

### 3. 🎙️ Voice Chat Trực Tiếp Qua UDP (Gọi Thoại Thời Gian Thực)
- **Âm thanh độ trễ cực thấp (Low-latency UDP Streaming):** Sử dụng thư viện chuẩn của Java `javax.sound.sampled`.
- **Thu âm Microphone (`TargetDataLine`):** Thu âm thanh từ Micro với định dạng chuẩn giọng nói 16kHz, 16-bit Mono PCM, đóng gói vào các `DatagramPacket` gửi qua cổng UDP thoại.
- **Phát âm thanh ra Loa (`SourceDataLine`):** Bên nhận liên tục nhận các gói tin âm thanh UDP và nạp thẳng vào bộ đệm của Loa/Tai nghe để phát ngay lập tức.
- **Giao diện điều khiển cuộc gọi trực quan:**
  - Nút **`📞 Gọi thoại`**: Gửi tín hiệu gọi điện thoại tới đối phương.
  - Hộp thoại nhận cuộc gọi: Bên nhận nghe tiếng chuông và có thể chọn **Đồng ý / Từ chối**.
  - Nút **`🔴 Ngắt máy`**: Kết thúc cuộc gọi bất cứ lúc nào.
  - Nút **`🎙️ Tắt/Bật Mic`**: Bật tắt micro khi đang trò chuyện để đảm bảo tính riêng tư.

### 4. 👥 Chat Đa Người Dùng (Multi-Peer / Group Chat & Mesh Network)
- **Phòng Chat Chung (All LAN):** Bất kỳ ai nhắn tin vào kênh chung, tin nhắn sẽ được gửi đồng thời tới toàn bộ các máy đang kết nối trong mạng LAN.
- **Chat Riêng 1-1:** Nhấp chọn một người dùng cụ thể trên danh sách bên trái để mở phiên nhắn tin riêng tư hoặc thực hiện cuộc gọi thoại trực tiếp.
- **Mã hóa E2EE (AES-GCM):** Mã hóa toàn bộ nội dung tin nhắn trước khi truyền qua mạng LAN.

---

## 📂 Danh Sách File & Cấu Trúc Mã Nguồn

```
c:\DA WEB\LTM\
├── run.bat                          // Chạy 1 ứng dụng
├── run-two-instances.bat            // Mở 2 máy chat cục bộ (May_A & May_B)
├── run-three-instances.bat          // Mở 3 máy chat chung (May_A, May_B, May_C)
├── pom.xml                          // Cấu hình Maven & đóng gói Uber JAR
└── src\
    ├── main\java\com\lan\chat\
    │   ├── Main.java                // Điểm khởi chạy chính
    │   ├── model\
    │   │   ├── Message.java         // Model tin nhắn
    │   │   ├── Peer.java            // Model thiết bị (IP, Port, VoicePort, AutoDiscovered)
    │   │   ├── PacketType.java      // Mã giao thức mạng (File Resume, Call Signaling)
    │   │   └── FileTransferTask.java// Quản lý truyền file, offset, .part, .meta, ETA
    │   ├── network\
    │   │   ├── TcpServer.java       // ServerSocket lắng nghe đa kết nối
    │   │   ├── PeerConnection.java  // Xử lý Socket, Chunking 8KB & Resume
    │   │   ├── ConnectionManager.java // Quản lý Mesh đa kết nối, Auto-connect, định tuyến
    │   │   ├── UdpDiscoveryService.java // Dò tìm tự động qua UDP Multicast/Broadcast
    │   │   └── ConnectionListener.java // Interface sự kiện mạng & Voice Call
    │   ├── voice\
    │   │   └── VoiceCallService.java// Dịch vụ gọi thoại UDP (TargetDataLine & SourceDataLine)
    │   ├── ui\
    │   │   ├── MainChatFrame.java   // Giao diện Swing chính với thanh Voice Call & Group Chat
    │   │   └── components\
    │   │       ├── ChatHistoryPanel.java // Bong bóng chat hiện đại
    │   │       ├── FileTransferBar.java  // Thanh tiến trình, nút Pause/Resume, ETA
    │   │       └── OnlinePeersPanel.java // Danh sách phòng chung & thiết bị online
    │   └── util\
    │       ├── NetworkUtil.java     // Nhận diện IP LAN IPv4, format dung lượng
    │       ├── SecurityUtil.java    // Mã hóa AES-GCM
    │       └── AudioNotifier.java   // Âm thanh chuông báo 'ting ting'
    └── test\java\com\lan\chat\
        └── NetworkTest.java         // Bài kiểm thử tự động toàn diện
```

---

## 🚀 Hướng Dẫn Thử Nghiệm

### Thử Nghiệm 3 Máy Chat Nhóm Cùng Lúc Trên 1 Máy Tính
Nhấp đúp vào file:
👉 **[run-three-instances.bat](file:///c:/DA%20WEB/LTM/run-three-instances.bat)**

1. Hệ thống sẽ mở 3 cửa sổ: **May_A** (cổng 5000), **May_B** (cổng 5001), **May_C** (cổng 5002).
2. Dịch vụ **UDP Auto-Discovery** sẽ tự động phát hiện các máy và hiển thị trong danh sách.
3. Khi nhắn tin vào **"Phòng Chat Chung"**, cả 3 máy sẽ nhận được tin nhắn tức thì!
4. Chọn một máy bất kỳ rồi bấm **`📞 Gọi thoại`** để thử nghiệm đàm thoại UDP qua Micro và Loa.
5. Bấm **`📁 Chọn File`** để thử nghiệm truyền file dung lượng lớn và kiểm tra tính năng **Tạm dừng / Tiếp tục (Resume)**.
