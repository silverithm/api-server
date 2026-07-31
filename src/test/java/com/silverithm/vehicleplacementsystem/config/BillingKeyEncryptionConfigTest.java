package com.silverithm.vehicleplacementsystem.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("빌링키 암호화 설정")
class BillingKeyEncryptionConfigTest {

    private static final String LEGACY_DEFAULT_KEY = "dGVzdGtleWZvcmJpbGxpbmdlbmNyeXB0aW9u";
    private static final String NEW_KEY = "Zm9yLXRlc3Qtb25seS1uZXctYmlsbGluZy1rZXktMzJieXRlcw==";

    private BillingKeyEncryptionConfig configWith(String key) {
        BillingKeyEncryptionConfig config = new BillingKeyEncryptionConfig();
        ReflectionTestUtils.setField(config, "encryptionKey", key);
        return config;
    }

    @Test
    @DisplayName("GCM 암복호화 왕복이 성립한다")
    void gcmRoundTrip() {
        BillingKeyEncryptionConfig config = configWith(NEW_KEY);
        SecretKey key = config.billingKeySecretKey();

        String plain = "billing_key_abc_123";
        String encrypted = config.encrypt(plain, key);

        assertThat(encrypted).startsWith("v2:");
        assertThat(config.decrypt(encrypted, key)).isEqualTo(plain);
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문이 된다 (ECB 패턴 노출 제거)")
    void samePlaintextProducesDifferentCiphertext() {
        BillingKeyEncryptionConfig config = configWith(NEW_KEY);
        SecretKey key = config.billingKeySecretKey();

        String plain = "billing_key_abc_123";

        assertThat(config.encrypt(plain, key)).isNotEqualTo(config.encrypt(plain, key));
    }

    @Test
    @DisplayName("변조된 암호문은 복호화에 실패한다 (무결성 검증)")
    void tamperedCiphertextFails() {
        BillingKeyEncryptionConfig config = configWith(NEW_KEY);
        SecretKey key = config.billingKeySecretKey();

        String encrypted = config.encrypt("billing_key_abc_123", key);
        String tampered = encrypted.substring(0, encrypted.length() - 2)
                + (encrypted.endsWith("A") ? "B=" : "A=");

        assertThatThrownBy(() -> config.decrypt(tampered, key))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("빌링키 복호화 실패");
    }

    @Test
    @DisplayName("기존 ECB 암호문은 새 키로 전환한 뒤에도 그대로 복호화된다")
    void legacyEcbCiphertextStillDecrypts() throws Exception {
        String plain = "legacy_billing_key_999";
        String legacyCiphertext = encryptWithLegacyEcb(plain);

        BillingKeyEncryptionConfig config = configWith(NEW_KEY);
        SecretKey newKey = config.billingKeySecretKey();

        assertThat(config.isLegacyFormat(legacyCiphertext)).isTrue();
        assertThat(config.decrypt(legacyCiphertext, newKey)).isEqualTo(plain);
    }

    @Test
    @DisplayName("암호화 키 미설정 시 기동에 실패한다")
    void blankKeyIsRejected() {
        assertThatThrownBy(() -> configWith("").billingKeySecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BILLING_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("과거 공개 기본키를 그대로 쓰면 기동에 실패한다")
    void legacyDefaultKeyIsRejected() {
        assertThatThrownBy(() -> configWith(LEGACY_DEFAULT_KEY).billingKeySecretKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기본값");
    }

    /** 마이그레이션 이전 형식(AES/ECB + 공개 기본키)을 그대로 재현한다. */
    private String encryptWithLegacyEcb(String plain) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(LEGACY_DEFAULT_KEY);
        byte[] hashed = MessageDigest.getInstance("SHA-256").digest(keyBytes);
        SecretKey legacyKey = new SecretKeySpec(hashed, "AES");

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }
}
