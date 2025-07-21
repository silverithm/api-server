package com.silverithm.vehicleplacementsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Configuration
public class BillingKeyEncryptionConfig {

    @Value("${billing.encryption.key:dGVzdGtleWZvcmJpbGxpbmdlbmNyeXB0aW9u}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Bean
    public SecretKey billingKeySecretKey() {
        try {
            // Base64 디코딩 시도
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(encryptionKey);
            } catch (IllegalArgumentException e) {
                // Base64가 아닌 경우 원본 문자열을 바이트로 변환
                keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
            }
            
            // SHA-256을 사용하여 32바이트 키 생성 (AES-256 호환)
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = sha256.digest(keyBytes);
            
            return new SecretKeySpec(hashedKey, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
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