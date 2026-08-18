package com.silverithm.vehicleplacementsystem.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 개인정보(어르신 이름·주소) 컬럼 암호화 키.
 *
 * <p>암호화 방식은 빌링키와 같은 AES-256-GCM({@code v2:} 접두사)을 쓰되 키는 분리한다 —
 * 결제 자격증명과 개인정보는 유출 시 대응 범위가 달라 키 교체를 따로 할 수 있어야 한다.
 * GCM 프리미티브는 {@link BillingKeyEncryptionConfig#encrypt}/{@code decrypt}를 재사용한다.
 *
 * <p>운영 키는 {@code PII_ENCRYPTION_KEY} 환경변수로 주입하며, 미설정 시 기동을 중단한다.
 * 키가 없는 채로 떠서 조용히 평문으로 저장되는 것이 가장 나쁜 실패 방식이기 때문이다.
 */
@Configuration
public class PiiEncryptionConfig {

    @Value("${pii.encryption.key:}")
    private String encryptionKey;

    @Bean
    public SecretKey piiSecretKey() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "개인정보 암호화 키가 설정되지 않았습니다. 환경변수 PII_ENCRYPTION_KEY(또는 프로퍼티 "
                            + "pii.encryption.key)를 지정하십시오. 생성 예: openssl rand -base64 32");
        }
        return deriveKey(encryptionKey);
    }

    /** 32바이트 AES 키 파생 — 빌링키와 같은 방식(Base64 디코드 후 SHA-256) */
    private SecretKey deriveKey(String rawKey) {
        try {
            byte[] keyBytes;
            try {
                keyBytes = Base64.getDecoder().decode(rawKey);
            } catch (IllegalArgumentException e) {
                keyBytes = rawKey.getBytes(StandardCharsets.UTF_8);
            }
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(sha256.digest(keyBytes), "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
    }
}
