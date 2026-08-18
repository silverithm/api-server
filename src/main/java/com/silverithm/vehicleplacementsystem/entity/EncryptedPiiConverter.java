package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 개인정보 문자열 컬럼 암호화 컨버터 (AES-256-GCM, {@code v2:} 접두사).
 *
 * <p>쓰기는 항상 암호화하고, 읽기는 접두사가 없으면 평문으로 간주해 그대로 돌려준다.
 * 배포 시점에 남아 있는 기존 평문 행을 깨뜨리지 않기 위한 하위 호환이며,
 * 그 행들은 {@link com.silverithm.vehicleplacementsystem.service.ElderPiiBackfillRunner}가
 * 기동 직후 암호문으로 바꿔 놓는다.
 *
 * <p>주의: 이 컬럼은 더 이상 DB에서 정렬·검색할 수 없다. 정렬은 조회 후 앱에서 한다.
 */
@Converter
@Component
@RequiredArgsConstructor
public class EncryptedPiiConverter implements AttributeConverter<String, String> {

    public static final String ENC_PREFIX = "v2:";

    /** GCM 암복호 프리미티브 재사용 — 키만 개인정보 전용({@code piiSecretKey})을 쓴다 */
    private final BillingKeyEncryptionConfig cryptoConfig;
    private final SecretKey piiSecretKey;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return attribute;
        }
        return cryptoConfig.encrypt(attribute, piiSecretKey);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        if (!dbData.startsWith(ENC_PREFIX)) {
            return dbData; // 아직 암호화되지 않은 기존 행
        }
        return cryptoConfig.decrypt(dbData, piiSecretKey);
    }
}
