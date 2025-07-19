package com.silverithm.vehicleplacementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class BillingKeyEncryptionConfig {

    @Value("${billing.encryption.key:dGVzdGtleWZvcmJpbGxpbmdlbmNyeXB0aW9u}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Bean
    public SecretKey billingKeySecretKey() {
        byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
        return new SecretKeySpec(decodedKey, ALGORITHM);
    }

    public String encrypt(String plainText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("빌링키 암호화 실패", e);
        }
    }

    public String decrypt(String encryptedText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("빌링키 복호화 실패", e);
        }
    }
}