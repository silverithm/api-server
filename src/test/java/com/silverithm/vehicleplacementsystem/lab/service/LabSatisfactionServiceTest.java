package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSatisfactionDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabSatisfactionResponseRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabSatisfactionServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @Autowired private LabSatisfactionService service;
    @Autowired private LabSatisfactionResponseRepository responses;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private Map<String, Integer> scores(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private LabSatisfactionDto.Input input(LocalDate date, String kind, Long elderlyId, Map<String, Integer> scores) {
        return new LabSatisfactionDto.Input(date, kind, elderlyId, "SELF", scores, "국이 싱거워요", "박사회");
    }

    @Test
    void scoresRangeValidated() {
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 0))),
                HttpStatus.BAD_REQUEST, "1~5점");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 6))),
                HttpStatus.BAD_REQUEST, "1~5점");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", null))),
                HttpStatus.BAD_REQUEST, "1~5점");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("meal_taste", 3))),
                HttpStatus.BAD_REQUEST, "문항 코드");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("M", 3))), HttpStatus.BAD_REQUEST,
                "문항 코드");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("A".repeat(31), 3))),
                HttpStatus.BAD_REQUEST, "문항 코드");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL TASTE", 3))),
                HttpStatus.BAD_REQUEST, "문항 코드");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), new LinkedHashMap<>())),
                HttpStatus.BAD_REQUEST, "하나 이상");
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), null)), HttpStatus.BAD_REQUEST, "하나 이상");
        Map<String, Integer> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 21; i++) {
            tooMany.put("Q" + i, 3);
        }
        assertRejected(() -> service.create(input(DAY, "MEAL", kim.getId(), tooMany)), HttpStatus.BAD_REQUEST, "20개");
        assertRejected(() -> service.create(input(DAY, "SNACK", kim.getId(), scores("MEAL_TASTE", 3))),
                HttpStatus.BAD_REQUEST, "종류");
        assertRejected(() -> service.create(new LabSatisfactionDto.Input(DAY, "MEAL", kim.getId(), "NEIGHBOR",
                scores("MEAL_TASTE", 3), null, null)), HttpStatus.BAD_REQUEST, "응답자");
        assertRejected(() -> service.create(input(null, "MEAL", kim.getId(), scores("MEAL_TASTE", 3))),
                HttpStatus.BAD_REQUEST, "응답일");
        // 미래 날짜는 400 — 고정한 먼 미래
        assertRejected(() -> service.create(input(LocalDate.of(2099, 1, 1), "MEAL", kim.getId(),
                scores("MEAL_TASTE", 3))), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(responses.count()).isZero();

        // 경계 1·5점, 정확히 20개는 허용
        Map<String, Integer> twenty = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            twenty.put("Q_" + i, i % 2 == 0 ? 1 : 5);
        }
        LabSatisfactionDto.View ok = service.create(input(DAY, "MEAL", kim.getId(), twenty));
        assertThat(ok.scores()).hasSize(20).containsEntry("Q_0", 1).containsEntry("Q_1", 5);
        assertThat(ok.kind()).isEqualTo("MEAL");

        // 수정도 같은 검증을 거치고, 거절되면 저장된 값이 그대로다
        assertRejected(() -> service.update(ok.id(), input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 9))),
                HttpStatus.BAD_REQUEST, "1~5점");
        assertRejected(() -> service.update(99999L, input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 3))),
                HttpStatus.NOT_FOUND, "만족도 응답");
        em.flush();
        em.clear();
        assertThat(service.list("MEAL", "2026-01-01", "2026-12-31").get(0).scores()).hasSize(20);
    }

    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.create(input(DAY, "MEAL", stranger.getId(), scores("MEAL_TASTE", 4))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.create(input(DAY, "MEAL", 987654L, scores("MEAL_TASTE", 4))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(responses.count()).isZero();

        LabSatisfactionDto.View mine = service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 4)));
        assertThat(mine.elderlyId()).isEqualTo(kim.getId());
        assertThat(mine.elderlyName()).isEqualTo("김순자");
        // 수정으로 남의 어르신을 붙이는 것도 막는다
        assertRejected(() -> service.update(mine.id(), input(DAY, "MEAL", stranger.getId(), scores("MEAL_TASTE", 4))),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");

        // 다른 기관에서는 보이지도 고치지도 지우지도 못한다
        access.companyId = other.getId();
        assertThat(service.list(null, "2026-01-01", "2026-12-31")).isEmpty();
        assertRejected(() -> service.delete(mine.id()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.update(mine.id(), input(DAY, "MEAL", stranger.getId(), scores("MEAL_TASTE", 4))),
                HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        assertThat(responses.count()).isEqualTo(1);
    }

    @Test
    void anonymousAllowed() {
        LabSatisfactionDto.View anonymous = service.create(new LabSatisfactionDto.Input(DAY, "PROGRAM", null,
                "GUARDIAN", scores("PROGRAM_FUN", 5, "PROGRAM_FIT", 4), null, null));
        assertThat(anonymous.elderlyId()).isNull();
        assertThat(anonymous.elderlyName()).isNull();
        assertThat(anonymous.respondent()).isEqualTo("GUARDIAN");
        assertThat(anonymous.comment()).isNull();
        assertThat(anonymous.interviewerName()).isNull();

        service.create(input(DAY, "PROGRAM", kim.getId(), scores("PROGRAM_FUN", 3)));
        em.flush();
        em.clear();
        assertThat(service.list("PROGRAM", "2026-03-01", "2026-03-31")).hasSize(2)
                .extracting(LabSatisfactionDto.View::elderlyName).containsExactlyInAnyOrder(null, "김순자");

        // 어르신을 지워도(DB는 SET NULL) 익명으로 남은 응답을 읽을 수 있다
        jdbc.update("UPDATE lab_satisfaction_responses SET elderly_id = NULL WHERE elderly_id IS NOT NULL");
        em.clear();
        assertThat(service.list("PROGRAM", "2026-03-01", "2026-03-31")).extracting(LabSatisfactionDto.View::elderlyId)
                .containsOnlyNulls();
        // 익명 응답을 어르신 응답으로 고칠 수도, 그 반대도 된다
        LabSatisfactionDto.View linked = service.update(anonymous.id(), input(DAY, "PROGRAM", kim.getId(),
                scores("PROGRAM_FUN", 5)));
        assertThat(linked.elderlyName()).isEqualTo("김순자");
        assertThat(service.update(anonymous.id(), new LabSatisfactionDto.Input(DAY, "PROGRAM", null, "SELF",
                scores("PROGRAM_FUN", 5), null, null)).elderlyId()).isNull();
    }

    @Test
    void commentEncryptedAtRest() {
        LabSatisfactionDto.View saved = service.create(input(DAY, "MEAL", kim.getId(), scores("MEAL_TASTE", 2)));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_satisfaction_responses", "comment", "국이 싱거워요");
        LabSatisfactionDto.View read = service.list("MEAL", "2026-03-01", "2026-03-31").get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.comment()).isEqualTo("국이 싱거워요");
        assertThat(read.interviewerName()).isEqualTo("박사회");
        assertThat(read.scores()).containsEntry("MEAL_TASTE", 2);
        // 의견이 1000자를 넘으면 400, 비우면 null
        assertRejected(() -> service.update(saved.id(), new LabSatisfactionDto.Input(DAY, "MEAL", kim.getId(), "SELF",
                scores("MEAL_TASTE", 2), "가".repeat(1001), null)), HttpStatus.BAD_REQUEST, "1000자");
        assertThat(service.update(saved.id(), new LabSatisfactionDto.Input(DAY, "MEAL", kim.getId(), "SELF",
                scores("MEAL_TASTE", 2), "  ", null)).comment()).isNull();
    }

    @Test
    void rangeQueryLimitedToOneYear() {
        LabSatisfactionDto.View meal = service.create(input(LocalDate.of(2026, 3, 10), "MEAL", kim.getId(),
                scores("MEAL_TASTE", 4)));
        LabSatisfactionDto.View program = service.create(input(LocalDate.of(2026, 5, 1), "PROGRAM", null,
                scores("PROGRAM_FUN", 4)));
        LabSatisfactionDto.View old = service.create(input(LocalDate.of(2025, 6, 1), "MEAL", kim.getId(),
                scores("MEAL_TASTE", 3)));

        // 기간 필수
        assertRejected(() -> service.list(null, null, "2026-12-31"), HttpStatus.BAD_REQUEST, "조회 기간");
        assertRejected(() -> service.list(null, "2026-01-01", null), HttpStatus.BAD_REQUEST, "조회 기간");
        assertRejected(() -> service.list(null, "", ""), HttpStatus.BAD_REQUEST, "조회 기간");
        assertRejected(() -> service.list(null, "2026/01/01", "2026-12-31"), HttpStatus.BAD_REQUEST, "YYYY-MM-DD");
        assertRejected(() -> service.list(null, "2026-12-31", "2026-01-01"), HttpStatus.BAD_REQUEST, "앞일 수 없습니다");
        // 1년 넘으면 400, 정확히 1년(양 끝 포함)은 허용
        assertRejected(() -> service.list(null, "2026-01-01", "2027-01-02"), HttpStatus.BAD_REQUEST, "최대 1년");
        assertRejected(() -> service.list(null, "2020-01-01", "2026-12-31"), HttpStatus.BAD_REQUEST, "최대 1년");
        assertThat(service.list(null, "2026-01-01", "2027-01-01")).extracting(LabSatisfactionDto.View::id)
                .containsExactly(program.id(), meal.id());

        // 종류 필터와 기간 경계(양 끝 포함), 최신순
        assertThat(service.list("MEAL", "2025-06-01", "2026-03-10")).extracting(LabSatisfactionDto.View::id)
                .containsExactly(meal.id(), old.id());
        assertThat(service.list("MEAL", "2026-03-11", "2026-12-31")).isEmpty();
        assertThat(service.list("PROGRAM", "2026-01-01", "2026-12-31")).extracting(LabSatisfactionDto.View::id)
                .containsExactly(program.id());
        assertRejected(() -> service.list("SNACK", "2026-01-01", "2026-12-31"), HttpStatus.BAD_REQUEST, "종류");

        service.delete(old.id());
        assertThat(responses.count()).isEqualTo(2);
    }
}
