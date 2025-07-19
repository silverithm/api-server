package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("빌링키 암호화 서비스 테스트")
class BillingKeyEncryptionServiceTest {

    @Mock
    private BillingKeyEncryptionConfig encryptionConfig;

    @Mock
    private SecretKey billingKeySecretKey;

    @InjectMocks
    private BillingKeyEncryptionService encryptionService;

    private final String testBillingKey = "billing_test_key_123456789";
    private final String encryptedBillingKey = "encrypted_billing_key_base64";

    @BeforeEach
    void setUp() {
        // 실제 SecretKey 객체 생성 (테스트용)
        String testKey = "dGVzdGtleWZvcmJpbGxpbmdlbmNyeXB0aW9u"; // Base64 encoded test key
        byte[] decodedKey = Base64.getDecoder().decode(testKey);
        SecretKey realSecretKey = new SecretKeySpec(decodedKey, "AES");
        
        // billingKeySecretKey 필드에 실제 SecretKey 설정
        try {
            java.lang.reflect.Field field = BillingKeyEncryptionService.class.getDeclaredField("billingKeySecretKey");
            field.setAccessible(true);
            field.set(encryptionService, realSecretKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("빌링키 암호화 성공")
    void encryptBillingKey_Success() {
        // Given
        when(encryptionConfig.encrypt(eq(testBillingKey), any(SecretKey.class)))
                .thenReturn(encryptedBillingKey);

        // When
        String result = encryptionService.encryptBillingKey(testBillingKey);

        // Then
        assertNotNull(result);
        assertEquals(encryptedBillingKey, result);
    }

    @Test
    @DisplayName("빌링키 복호화 성공")
    void decryptBillingKey_Success() {
        // Given
        when(encryptionConfig.decrypt(eq(encryptedBillingKey), any(SecretKey.class)))
                .thenReturn(testBillingKey);

        // When
        String result = encryptionService.decryptBillingKey(encryptedBillingKey);

        // Then
        assertNotNull(result);
        assertEquals(testBillingKey, result);
    }

    @Test
    @DisplayName("null 빌링키 암호화 시 null 반환")
    void encryptBillingKey_NullInput() {
        // When
        String result = encryptionService.encryptBillingKey(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("빈 문자열 빌링키 암호화 시 null 반환")
    void encryptBillingKey_EmptyInput() {
        // When
        String result = encryptionService.encryptBillingKey("");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("공백만 있는 빌링키 암호화 시 null 반환")
    void encryptBillingKey_BlankInput() {
        // When
        String result = encryptionService.encryptBillingKey("   ");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("null 암호화된 빌링키 복호화 시 null 반환")
    void decryptBillingKey_NullInput() {
        // When
        String result = encryptionService.decryptBillingKey(null);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("빈 문자열 암호화된 빌링키 복호화 시 null 반환")
    void decryptBillingKey_EmptyInput() {
        // When
        String result = encryptionService.decryptBillingKey("");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("암호화 중 예외 발생")
    void encryptBillingKey_Exception() {
        // Given
        when(encryptionConfig.encrypt(eq(testBillingKey), any(SecretKey.class)))
                .thenThrow(new RuntimeException("암호화 실패"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            encryptionService.encryptBillingKey(testBillingKey);
        });

        assertEquals("빌링키 암호화 실패", exception.getMessage());
    }

    @Test
    @DisplayName("복호화 중 예외 발생")
    void decryptBillingKey_Exception() {
        // Given
        when(encryptionConfig.decrypt(eq(encryptedBillingKey), any(SecretKey.class)))
                .thenThrow(new RuntimeException("복호화 실패"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            encryptionService.decryptBillingKey(encryptedBillingKey);
        });

        assertEquals("빌링키 복호화 실패", exception.getMessage());
    }

    @Test
    @DisplayName("암호화-복호화 라운드 트립 테스트")
    void encryptDecrypt_RoundTrip() {
        // Given
        when(encryptionConfig.encrypt(eq(testBillingKey), any(SecretKey.class)))
                .thenReturn(encryptedBillingKey);
        when(encryptionConfig.decrypt(eq(encryptedBillingKey), any(SecretKey.class)))
                .thenReturn(testBillingKey);

        // When
        String encrypted = encryptionService.encryptBillingKey(testBillingKey);
        String decrypted = encryptionService.decryptBillingKey(encrypted);

        // Then
        assertNotNull(encrypted);
        assertNotNull(decrypted);
        assertEquals(testBillingKey, decrypted);
        assertNotEquals(testBillingKey, encrypted); // 암호화된 값은 원본과 달라야 함
    }
}