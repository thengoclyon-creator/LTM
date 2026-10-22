package com.lan.chat.history;

import com.lan.chat.model.Message;
import com.lan.chat.model.MessageType;
import com.lan.chat.model.Peer;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class ChatHistoryTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== KIỂM THỬ TÍNH NĂNG LỊCH SỬ CHAT (CHAT HISTORY) ===");

        File testDir = Files.createTempDirectory("lan_chat_history_test").toFile();
        testDir.deleteOnExit();

        ChatHistoryManager manager = new ChatHistoryManager(testDir);

        // 1. Lưu tin nhắn phòng chung
        System.out.println("1. Kiểm tra lưu tin nhắn phòng chung (Group Chat)...");
        Message m1 = Message.userText("Alice", "Chào mọi người trong mạng LAN!", false, false);
        Message m2 = Message.userText("Bob", "Chào Alice, mình nhận được rồi!", true, true);
        manager.saveMessage(ChatHistoryManager.GROUP_CONVERSATION_KEY, m1);
        manager.saveMessage(ChatHistoryManager.GROUP_CONVERSATION_KEY, m2);

        List<Message> groupMsgs = manager.getMessages(ChatHistoryManager.GROUP_CONVERSATION_KEY);
        if (groupMsgs.size() != 2) {
            throw new RuntimeException("Lỗi: Số lượng tin nhắn phòng chung không đúng, kỳ vọng 2 nhưng có " + groupMsgs.size());
        }
        if (!"Chào mọi người trong mạng LAN!".equals(groupMsgs.get(0).getContent())) {
            throw new RuntimeException("Lỗi nội dung tin nhắn 1!");
        }
        if (!groupMsgs.get(1).isEncrypted()) {
            throw new RuntimeException("Lỗi cờ mã hóa E2EE!");
        }
        System.out.println("-> Lưu & Đọc lịch sử phòng chung: OK!");

        // 2. Lưu tin nhắn riêng 1-1 với Peer
        System.out.println("2. Kiểm tra lưu tin nhắn chat riêng (1-1 Private Chat)...");
        Peer charlie = new Peer("192.168.1.50", 5005, "Charlie");
        String charlieKey = ChatHistoryManager.getConversationKey(charlie);

        Message m3 = Message.userText("Charlie", "Đây là tin nhắn riêng tư bí mật", false, true);
        manager.saveMessage(charlieKey, m3);

        List<Message> charlieMsgs = manager.getMessages(charlieKey);
        if (charlieMsgs.size() != 1 || !charlieMsgs.get(0).getContent().contains("bí mật")) {
            throw new RuntimeException("Lỗi lưu tin nhắn riêng 1-1!");
        }
        System.out.println("-> Lưu & Đọc lịch sử chat riêng 1-1: OK!");

        // 3. Kiểm tra tính năng tìm kiếm (Search)
        System.out.println("3. Kiểm tra tìm kiếm từ khóa...");
        List<ChatHistoryManager.SearchResult> searchResults = manager.searchMessages("bí mật", null);
        if (searchResults.isEmpty()) {
            throw new RuntimeException("Tìm kiếm thất bại cho từ khóa 'bí mật'!");
        }
        System.out.println("-> Tìm thấy " + searchResults.size() + " kết quả: " + searchResults.get(0).message().getContent());

        // 4. Kiểm tra xuất file TXT & HTML (Export)
        System.out.println("4. Kiểm tra xuất file báo cáo TXT và HTML...");
        File txtFile = new File(testDir, "test_export.txt");
        File htmlFile = new File(testDir, "test_export.html");

        boolean exportTxtOk = manager.exportToText(ChatHistoryManager.GROUP_CONVERSATION_KEY, txtFile);
        boolean exportHtmlOk = manager.exportToHtml(ChatHistoryManager.GROUP_CONVERSATION_KEY, htmlFile);

        if (!exportTxtOk || !txtFile.exists() || txtFile.length() == 0) {
            throw new RuntimeException("Xuất file TXT thất bại!");
        }
        if (!exportHtmlOk || !htmlFile.exists() || htmlFile.length() == 0) {
            throw new RuntimeException("Xuất file HTML thất bại!");
        }
        System.out.println("-> Xuất file TXT dung lượng: " + txtFile.length() + " bytes");
        System.out.println("-> Xuất file HTML dung lượng: " + htmlFile.length() + " bytes");

        // 5. Kiểm tra xóa lịch sử
        System.out.println("5. Kiểm tra xóa lịch sử cuộc trò chuyện...");
        manager.clearConversation(charlieKey);
        List<Message> clearedMsgs = manager.getMessages(charlieKey);
        if (!clearedMsgs.isEmpty()) {
            throw new RuntimeException("Lỗi: Lịch sử chưa được xóa sạch!");
        }
        System.out.println("-> Xóa lịch sử hội thoại: OK!");

        System.out.println("=== TẤT CẢ KIỂM THỬ LỊCH SỬ CHAT ĐÃ PASS 100%! ===");
    }
}
