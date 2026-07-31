package com.silverithm.vehicleplacementsystem.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 빌링키(결제 수단 자격증명) 암호화.
 *
 * <p>신규 암호문은 AES-256-GCM(랜덤 IV + 인증 태그)으로 생성하고 {@code v2:} 접두사를 붙인다.
 * 접두사가 없는 값은 과거 AES-ECB 형식이므로 레거시 키로 복호화해 하위 호환을 유지한다.
 *
 * <p>ECB는 동일 평문이 동일 암호문이 되어 패턴이 드러나고 무결성 검증도 없으므로 신규 사용은 금지한다.
 * 또한 과거에는 키가 코드에 기본값으로 박혀 있어(공개된 값) 사실상 평문과 다름없었다.
 * 따라서 운영 키는 반드시 {@code BILLING_ENCRYPTION_KEY} 환경변수로 주입해야 하며,
 * 미설정 시 기동을 중단한다.
 */
@Configuration
@Slf4j
public class BillingKeyEncryptionConfig {

    /** 과거 코드에 기본값으로 박혀 있던 키. 기존 데이터 복호화 용도로만 남긴다. */
    private static final String LEGACY_DEFAULT_KEY = "dGVzdGtleWZvcmJpbGxpbmdlbmNyeXB0aW9u";

    private static final String ALGORITHM = "AES";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /** 신규 암호문 식별용 접두사 */
    private static final String V2_PREFIX = "v2:";

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${billing.encryption.key:}")
    private String encryptionKey;

    @Bean
    public SecretKey billingKeySecretKey() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "빌링키 암호화 키가 설정되지 않았습니다. 환경변수 BILLING_ENCRYPTION_KEY(또는 프로퍼티 "
                            + "billing.encryption.key)를 지정하십시오. 생성 예: openssl rand -base64 32");
        }

        if (LEGACY_DEFAULT_KEY.equals(encryptionKey.trim())) {
            throw new IllegalStateException(
                    "빌링키 암호화 키가 과거 코드에 공개돼 있던 기본값과 동일합니다. 반드시 새 키로 교체하십시오. "
                            + "생성 예: openssl rand -base64 32");
        }

        return deriveKey(encryptionKey);
    }

    /** 레거시(ECB) 암호문 복호화 전용 키 */
    private SecretKey legacyKey() {
        return deriveKey(LEGACY_DEFAULT_KEY);
    }

    /**
     * 32바이트 AES 키 파생.
     * 기존 데이터와의 호환을 위해 파생 방식(Base64 디코드 후 SHA-256)은 변경하지 않는다.
     */
    private SecretKey deriveKey(String rawKey) {
        try {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(rawKey);
            } catch (IllegalArgumentException e) {
                // Base64가 아닌 경우 원본 문자열을 바이트로 변환
                keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
            }

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hashedKey = sha256.digest(keyBytes);

            return new SecretKeySpec(hashedKey, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
    }

    /** AES-256-GCM 암호화. 결과는 {@code v2:base64(iv || ciphertext+tag)} */
    public String encrypt(String plainText, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

            return V2_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("빌링키 암호화 실패", e);
        }
    }

    /** 암호문 형식에 따라 GCM(v2) 또는 레거시 ECB(v1)로 복호화한다. */
    public String decrypt(String encryptedText, SecretKey key) {
        if (encryptedText != null && encryptedText.startsWith(V2_PREFIX)) {
            return decryptGcm(encryptedText.substring(V2_PREFIX.length()), key);
        }
        return decryptLegacyEcb(encryptedText);
    }

    /** 재암호화가 필요한 레거시 형식인지 여부 */
    public boolean isLegacyFormat(String encryptedText) {
        return encryptedText != null && !encryptedText.isBlank() && !encryptedText.startsWith(V2_PREFIX);
    }

    private String decryptGcm(String base64Payload, SecretKey key) {
        try {
            byte[] payload = Base64.getDecoder().decode(base64Payload);
            if (payload.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("암호문 길이가 올바르지 않습니다");
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[payload.length - GCM_IV_LENGTH];
            System.arraycopy(payload, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("빌링키 복호화 실패", e);
        }
    }

    private String decryptLegacyEcb(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, legacyKey());
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("빌링키 복호화 실패", e);
        }
    }
}
