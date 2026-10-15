package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabEvalCheckDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabEvalCheckRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabEvalCheckServiceTest {

    @Autowired private LabEvalCheckService service;
    @Autowired private LabEvalCheckRepository checks;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Long companyId;

    @BeforeEach
    void setUp() {
        companyId = LabTestFixtures.company(em, "숲속").getId();
        access.companyId = companyId;
    }

    // 같은 지표를 다시 저장하면 한 건만 남고 내용이 바뀐다
    @Test
    void upsertByIndicatorCode() {
        service.upsert("SAFE-01", new LabEvalCheckDto.Input("PARTIAL", "낙상 평가 반기 1회", null));
        LabEvalCheckDto.View second = service.upsert("SAFE-01",
                new LabEvalCheckDto.Input("READY", "낙상·욕창 평가 완료", "근거 충분"));
        service.upsert("OPS_02", new LabEvalCheckDto.Input("MISSING", null, null));

        assertThat(checks.count()).isEqualTo(2);
        assertThat(second.status()).isEqualTo("READY");
        assertThat(second.evidence()).isEqualTo("낙상·욕창 평가 완료");
        assertThat(second.updatedBy()).isEqualTo("숲속 관리자");
        assertThat(service.list()).extracting(LabEvalCheckDto.View::indicatorCode)
                .containsExactly("OPS_02", "SAFE-01");

        em.flush();
        em.clear();
        String stored = jdbc.queryForObject(
                "SELECT evidence FROM lab_eval_checks WHERE indicator_code = 'SAFE-01'", String.class);
        assertThat(stored).startsWith(EncryptedPiiConverter.ENC_PREFIX).doesNotContain("낙상");
    }

    // 지표 코드 형식이 틀리거나 상태가 목록에 없으면 400
    @Test
    void invalidIndicatorCodeRejected() {
        for (String code : new String[] {"a", "lower-case", "HAS SPACE", "X".repeat(41), "한글코드", "A"}) {
            assertThatThrownBy(() -> service.upsert(code, new LabEvalCheckDto.Input("READY", null, null)))
                    .as(code)
                    .isInstanceOfSatisfying(CustomException.class,
                            e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThatThrownBy(() -> service.upsert("SAFE-01", new LabEvalCheckDto.Input("DONE", null, null)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert("SAFE-01",
                new LabEvalCheckDto.Input("READY", "가".repeat(3001), null))).isInstanceOf(CustomException.class);
        assertThat(checks.count()).isZero();
    }
}
