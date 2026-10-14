package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabNursingLogDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabNursingLogRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabNursingLogServiceTest {

    @Autowired private LabNursingLogService service;
    @Autowired private LabNursingLogRepository logs;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private LabNursingLogDto.Input input(Integer sys, Integer dia, Integer pulse, String temp, Integer sugar,
                                         String weight, String treatment, String note) {
        return new LabNursingLogDto.Input(kim.getId(), DAY, sys, dia, pulse,
                temp == null ? null : new BigDecimal(temp), sugar, weight == null ? null : new BigDecimal(weight),
                "GIVEN", "SKIPPED", null, treatment, note, "박간호", true);
    }

    private void assertBad(LabNursingLogDto.Input in) {
        assertThatThrownBy(() -> service.upsert(in)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // 같은 어르신·같은 날을 다시 저장하면 일지가 늘지 않고 내용만 바뀐다
    @Test
    void upsertKeepsOneLogPerElderAndDate() {
        LabNursingLogDto.View first = service.upsert(input(130, 80, 72, "36.5", 110, "52.3", "상처 소독", null));
        LabNursingLogDto.View second = service.upsert(input(142, 90, 80, "36.8", null, null, null, "어지러움"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(logs.count()).isEqualTo(1);
        assertThat(second.systolic()).isEqualTo(142);
        assertThat(second.bloodSugar()).isNull();
        assertThat(second.treatment()).isNull();
        assertThat(second.temperature()).isEqualByComparingTo("36.8");
        assertThat(second.elderlyName()).isEqualTo("김순자");
        assertThat(second.medMorning()).isEqualTo("GIVEN");

        service.upsert(new LabNursingLogDto.Input(kim.getId(), DAY.plusDays(1), 120, 70, null, null, null, null,
                null, null, null, null, null, null, null));
        assertThat(service.listByDate("2026-10-14")).hasSize(1);
        // 월 경계: 말일과 다음 달 1일이 섞이지 않는다
        service.upsert(new LabNursingLogDto.Input(kim.getId(), LocalDate.of(2026, 10, 31), 120, 70, null, null,
                null, null, null, null, null, null, null, null, null));
        service.upsert(new LabNursingLogDto.Input(kim.getId(), LocalDate.of(2026, 11, 1), 120, 70, null, null,
                null, null, null, null, null, null, null, null, null));
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-10"))
                .extracting(LabNursingLogDto.View::logDate)
                .containsExactly(DAY, DAY.plusDays(1), LocalDate.of(2026, 10, 31));
        assertThat(service.listByElderAndMonth(kim.getId(), "2026-11")).hasSize(1);
    }

    // 계약의 범위 경계: 안쪽 끝값은 저장되고, 한 칸 벗어나면 400
    @Test
    void outOfRangeVitalRejected() {
        assertThat(service.upsert(input(260, 160, 200, "42.0", 600, "200.0", null, null)).systolic()).isEqualTo(260);
        assertThat(service.upsert(input(50, 30, 30, "34.0", 20, "20.0", null, null)).diastolic()).isEqualTo(30);

        assertBad(input(261, 80, null, null, null, null, null, null));
        assertBad(input(49, 30, null, null, null, null, null, null));
        assertBad(input(120, 161, null, null, null, null, null, null));
        assertBad(input(null, 29, null, null, null, null, null, null));
        assertBad(input(null, null, 29, null, null, null, null, null));
        assertBad(input(null, null, 201, null, null, null, null, null));
        assertBad(input(null, null, null, "33.9", null, null, null, null));
        assertBad(input(null, null, null, "42.1", null, null, null, null));
        assertBad(input(null, null, null, null, 19, null, null, null));
        assertBad(input(null, null, null, null, 601, null, null, null));
        assertBad(input(null, null, null, null, null, "19.9", null, null));
        assertBad(input(null, null, null, null, null, "200.1", null, null));
        // 열이 담지 못하는 소수 둘째 자리는 조용히 반올림하지 않고 거절한다
        assertBad(input(null, null, null, "36.55", null, null, null, null));
        // 정수 범위를 크게 벗어난 값도 500이 아니라 400
        assertBad(input(Integer.MAX_VALUE, null, null, null, null, null, null, null));
    }

    // 수축기 < 이완기는 두 칸을 바꿔 적은 것이라 400. 같거나 한쪽만 있으면 통과
    @Test
    void systolicBelowDiastolicRejected() {
        assertThatThrownBy(() -> service.upsert(input(80, 120, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("수축기").contains("이완기");
                });
        assertThat(logs.count()).isZero();
        assertThat(service.upsert(input(90, 90, null, null, null, null, null, null)).id()).isNotNull();
        assertThat(service.upsert(input(null, 120, null, null, null, null, null, null)).diastolic()).isEqualTo(120);
    }

    // 처치와 메모는 DB에 암호문으로 들어가고 읽을 때만 평문이 된다 (원시 열 값을 직접 읽는다)
    @Test
    void notesAreEncryptedAtRest() {
        String treatment = "욕창 2단계 드레싱 교체 후 체위 변경";
        String note = "김순자 어르신 오후에 혈압 상승 호소";
        service.upsert(input(150, 95, 80, "36.6", 120, "50.0", treatment, note));
        em.flush();
        em.clear();

        for (String column : List.of("treatment", "note")) {
            String stored = jdbc.queryForObject("SELECT " + column + " FROM lab_nursing_logs", String.class);
            assertThat(stored).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT treatment FROM lab_nursing_logs", String.class))
                .doesNotContain("욕창").isNotEqualTo(treatment);
        assertThat(jdbc.queryForObject("SELECT note FROM lab_nursing_logs", String.class)).doesNotContain("혈압");

        LabNursingLogDto.View read = service.listByDate("2026-10-14").get(0);
        assertThat(read.treatment()).isEqualTo(treatment);
        assertThat(read.note()).isEqualTo(note);
    }

    // 다른 기관 어르신은 404, 잘못된 투약 값·긴 글은 400, 삭제는 자기 기관만
    @Test
    void rejectsForeignElderBadMedAndScopesDelete() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertThatThrownBy(() -> service.upsert(new LabNursingLogDto.Input(stranger.getId(), DAY, null, null, null,
                null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.listByElderAndMonth(stranger.getId(), "2026-10"))
                .isInstanceOf(CustomException.class);

        assertBad(new LabNursingLogDto.Input(kim.getId(), DAY, null, null, null, null, null, null, "TAKEN", null,
                null, null, null, null, null));
        assertBad(input(null, null, null, null, null, null, "가".repeat(2001), null));
        assertBad(new LabNursingLogDto.Input(kim.getId(), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        LabNursingLogDto.View saved = service.upsert(input(120, 80, null, null, null, null, null, null));
        access.companyId = forest.getId() + 1000;
        assertThatThrownBy(() -> service.delete(saved.id())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        access.companyId = forest.getId();
        service.delete(saved.id());
        assertThat(logs.count()).isZero();
    }
}
