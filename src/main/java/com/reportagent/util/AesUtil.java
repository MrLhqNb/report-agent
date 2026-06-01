package com.reportagent.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AesUtil {

    private final SecretKeySpec keySpec;

    public AesUtil(@Value("${app.encrypt-key}") String key) {
        byte[] keyBytes = new byte[16];
        byte[] src = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 16));
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Encrypt failed", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed", e);
        }
    }
}
