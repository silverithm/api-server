package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실험실 테스트 공용 확인 도구 — 거절(상태·메시지)과 "저장된 열이 평문이 아님" 확인 */
final class LabTestAsserts {

    private LabTestAsserts() {
    }

    static void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    /** DB에 실제로 저장된 열 값이 암호문(v2: 접두)이고 평문이 들어 있지 않은지 직접 읽어 확인한다 */
    static void assertEncryptedAtRest(JdbcTemplate jdbc, String table, String column, String plain) {
        String stored = jdbc.queryForObject("SELECT " + column + " FROM " + table + " LIMIT 1", String.class);
        assertThat(stored).as(table + "." + column).isNotNull().isNotEqualTo(plain)
                .startsWith(EncryptedPiiConverter.ENC_PREFIX).doesNotContain(plain);
    }
}
