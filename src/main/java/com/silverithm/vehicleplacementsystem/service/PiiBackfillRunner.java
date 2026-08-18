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
 * 개인정보 평문 컬럼을 암호문으로 바꾸는 기동 시 백필.
 * 어르신(이름·주소), 직원(이름·주소), 회원(이름·전화번호)을 다룬다.
 *
 * <p>컨버터는 읽을 때 평문을 그대로 통과시키므로 서비스는 백필 전에도 정상 동작한다.
 * 이 러너는 남은 평문 행만 골라 암호화해 두는 마무리 작업이며, 멱등이라 매 기동마다
 * 돌아도 이미 끝났으면 테이블당 조회 한 번으로 끝난다.
 *
 * <p>JPA 재저장 방식은 값이 그대로라 더티체킹에 걸리지 않아 UPDATE가 나가지 않는다.
 * 그래서 JDBC로 원문을 직접 읽어 암호화해 갱신한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PiiBackfillRunner implements ApplicationRunner {

    /** 테이블별 백필 대상: 테이블명, PK 컬럼, 암호화할 컬럼들 */
    private record Target(String table, String idColumn, List<String> columns) {
    }

    private static final List<Target> TARGETS = List.of(
            new Target("elderly", "node_id", List.of("name", "home_address_name")),
            new Target("employee", "node_id", List.of("name", "home_address_name")),
            new Target("members", "id", List.of("name", "phone_number"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final BillingKeyEncryptionConfig cryptoConfig;
    private final SecretKey piiSecretKey;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Target target : TARGETS) {
            backfill(target);
        }
    }

    private void backfill(Target target) {
        String where = target.columns().stream()
                .map(c -> "(" + c + " IS NOT NULL AND " + c + " <> '' AND " + c + " NOT LIKE 'v2:%')")
                .reduce((a, b) -> a + " OR " + b)
                .orElseThrow();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + target.idColumn() + ", " + String.join(", ", target.columns())
                        + " FROM " + target.table() + " WHERE " + where);
        if (rows.isEmpty()) {
            return;
        }

        String setClause = target.columns().stream()
                .map(c -> c + " = ?")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        int updated = 0;
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get(target.idColumn())).longValue();
            Object[] params = new Object[target.columns().size() + 1];
            for (int i = 0; i < target.columns().size(); i += 1) {
                params[i] = encryptIfPlain((String) row.get(target.columns().get(i)));
            }
            params[target.columns().size()] = id;
            jdbcTemplate.update(
                    "UPDATE " + target.table() + " SET " + setClause + " WHERE " + target.idColumn() + " = ?",
                    params);
            updated += 1;
        }

        log.info("[PII Backfill] {} 평문 {}건을 암호화했습니다.", target.table(), updated);
    }

    private String encryptIfPlain(String value) {
        if (value == null || value.isBlank() || value.startsWith(EncryptedPiiConverter.ENC_PREFIX)) {
            return value;
        }
        return cryptoConfig.encrypt(value, piiSecretKey);
    }
}
