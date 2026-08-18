package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.BillingKeyEncryptionConfig;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어르신 이름·주소 평문 행을 암호문으로 바꾸는 기동 시 백필.
 *
 * <p>컨버터는 읽을 때 평문을 그대로 통과시키므로 서비스는 백필 전에도 정상 동작한다.
 * 이 러너는 남은 평문 행만 골라 암호화해 두는 마무리 작업이며, 멱등이라 매 기동마다
 * 돌아도 이미 끝났으면 조회 한 번으로 끝난다.
 *
 * <p>JPA 재저장 방식은 값이 그대로라 더티체킹에 걸리지 않아 UPDATE가 나가지 않는다.
 * 그래서 JDBC로 원문을 직접 읽어 암호화해 갱신한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ElderPiiBackfillRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final BillingKeyEncryptionConfig cryptoConfig;
    private final SecretKey piiSecretKey;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT node_id, name, home_address_name FROM elderly "
                        + "WHERE (name IS NOT NULL AND name <> '' AND name NOT LIKE 'v2:%') "
                        + "   OR (home_address_name IS NOT NULL AND home_address_name <> '' "
                        + "       AND home_address_name NOT LIKE 'v2:%')");
        if (rows.isEmpty()) {
            return;
        }

        int updated = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("node_id")).longValue();
            String name = (String) row.get("name");
            String address = (String) row.get("home_address_name");

            String encryptedName = encryptIfPlain(name);
            String encryptedAddress = encryptIfPlain(address);

            jdbcTemplate.update("UPDATE elderly SET name = ?, home_address_name = ? WHERE node_id = ?",
                    encryptedName, encryptedAddress, id);
            updated += 1;
        }

        log.info("[PII Backfill] 어르신 개인정보 평문 {}건을 암호화했습니다.", updated);
    }

    private String encryptIfPlain(String value) {
        if (value == null || value.isBlank() || value.startsWith(EncryptedPiiConverter.ENC_PREFIX)) {
            return value;
        }
        return cryptoConfig.encrypt(value, piiSecretKey);
    }
}
