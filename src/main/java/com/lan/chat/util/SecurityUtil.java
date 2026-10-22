package com.lan.chat.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * End-to-End Encryption (E2EE) helper using AES-GCM (128-bit / 256-bit).
 * Messages are encrypted with AES/GCM/NoPadding before being sent across TCP.
 * Wireshark LAN capture will only reveal random ciphertext!
 */
public class SecurityUtil {

    private static final String DEFAULT_ROOM_SECRET = "LAN_CHAT_SECRET_KEY_2026";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private static SecretKeySpec secretKeySpec;

    static {
        setSecretPassphrase(DEFAULT_ROOM_SECRET);
    }

    public static synchronized void setSecretPassphrase(String passphrase) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            // Use 16 bytes for AES-128 or 32 bytes for AES-256
            secretKeySpec = new SecretKeySpec(keyBytes, 0, 16, "AES");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypt plaintext string with AES-GCM.
     * Returns Base64-encoded string containing [IV (12 bytes) + CipherText].
     */
    public static String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;

        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, parameterSpec);

        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Combine IV + CipherText
        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypt Base64 ciphertext with AES-GCM.
     */
    public static String decrypt(String base64CipherText) throws Exception {
        if (base64CipherText == null) return null;

        byte[] combined = Base64.getDecoder().decode(base64CipherText);
        if (combined.length < IV_LENGTH) {
            throw new IllegalArgumentException("Dữ liệu mã hóa không hợp lệ");
        }

        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

        int cipherTextLength = combined.length - IV_LENGTH;
        byte[] cipherText = new byte[cipherTextLength];
        System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherTextLength);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, parameterSpec);

        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }
}
