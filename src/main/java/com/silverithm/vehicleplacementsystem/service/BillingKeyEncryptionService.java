package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingKeyEncryptionService {

    private final BillingKeyEncryptionConfig encryptionConfig;
    private final SecretKey billingKeySecretKey;

    public String encryptBillingKey(String plainBillingKey) {
        if (plainBillingKey == null || plainBillingKey.trim().isEmpty()) {
            return null;
        }
        
        try {
            String encrypted = encryptionConfig.encrypt(plainBillingKey, billingKeySecretKey);
            log.debug("빌링키 암호화 완료");
            return encrypted;
        } catch (Exception e) {
            log.error("빌링키 암호화 실패: {}", e.getMessage());
            throw new RuntimeException("빌링키 암호화 실패", e);
        }
    }

    public String decryptBillingKey(String encryptedBillingKey) {
        if (encryptedBillingKey == null || encryptedBillingKey.trim().isEmpty()) {
            return null;
        }
        
        try {
            String decrypted = encryptionConfig.decrypt(encryptedBillingKey, billingKeySecretKey);
            log.debug("빌링키 복호화 완료");
            return decrypted;
        } catch (Exception e) {
            log.error("빌링키 복호화 실패: {}", e.getMessage());
            throw new RuntimeException("빌링키 복호화 실패", e);
        }
    }
}