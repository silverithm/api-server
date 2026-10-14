package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicalVisitDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicalVisitRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabMedicalVisitServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 1, 1);

    @Autowired private LabMedicalVisitService service;
    @Autowired private LabMedicalVisitRepository visits;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        park = LabTestFixtures.elder(em, forest, "박어르신");
        access.companyId = forest.getId();
    }

    private LabMedicalVisitDto.Input input(Long elderlyId, LocalDate date, String start, String end,
                                           String hospital, String result, String escort, LocalDate followUpOn,
                                           Boolean followUpDone) {
        return new LabMedicalVisitDto.Input(elderlyId, date, start, end, hospital, "내과", "혈압 조절 상담",
                result, "혈압약 7일분", escort, true, "오후에 전화로 알림", followUpOn, followUpDone, false);
    }

    private LabMedicalVisitDto.Input basic(LocalDate date) {
        return input(kim.getId(), date, "10:00", "11:30", "숲속내과", "혈압 안정, 약 유지", "이요양", null, null);
    }

    // 진료일은 오늘(서울) 이후일 수 없다 — 먼 미래로 확인하고, 오늘·과거는 통과
    @Test
    void futureDateRejected() {
        assertRejected(() -> service.create(basic(FAR_FUTURE)), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.create(basic(LabValidation.seoulToday().plusDays(1))), HttpStatus.BAD_REQUEST,
                "오늘 이후");
        assertRejected(() -> service.create(basic(null)), HttpStatus.BAD_REQUEST, "진료일");
        assertThat(visits.count()).isZero();

        assertThat(service.create(basic(DAY)).visitDate()).isEqualTo(DAY);
        assertThat(service.create(basic(LabValidation.seoulToday())).id()).isNotNull();
        // 고칠 때도 같은 규칙
        LabMedicalVisitDto.View saved = service.create(basic(DAY));
        assertRejected(() -> service.update(saved.id(), basic(FAR_FUTURE)), HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(service.update(saved.id(), basic(DAY.minusDays(1))).visitDate()).isEqualTo(DAY.minusDays(1));
    }

    // 둘 다 있으면 끝 > 시작. 하나만 있거나 둘 다 없으면 통과하고, 형식이 틀리면 400
    @Test
    void endBeforeStartRejected() {
        assertRejected(() -> service.create(input(kim.getId(), DAY, "11:00", "10:00", "숲속내과", "진료", "이요양",
                null, null)), HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 늦어야");
        assertRejected(() -> service.create(input(kim.getId(), DAY, "10:00", "10:00", "숲속내과", "진료", "이요양",
                null, null)), HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 늦어야");
        assertRejected(() -> service.create(input(kim.getId(), DAY, "25:00", null, "숲속내과", "진료", "이요양",
                null, null)), HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.create(input(kim.getId(), DAY, "9:30", null, "숲속내과", "진료", "이요양",
                null, null)), HttpStatus.BAD_REQUEST, "HH:MM");
        assertThat(visits.count()).isZero();

        assertThat(service.create(input(kim.getId(), DAY, "09:30", "10:00", "숲속내과", "진료", "이요양", null, null))
                .endTime()).isEqualTo("10:00");
        assertThat(service.create(input(kim.getId(), DAY, "09:30", null, "숲속내과", "진료", "이요양", null, null))
                .endTime()).isNull();
        assertThat(service.create(input(kim.getId(), DAY, null, "", "숲속내과", "진료", "이요양", null, null))
                .startTime()).isNull();
    }

    // 병원·진료 내용·동행 직원은 필수(평가 30 기준 1) — 빈 칸·공백은 거절
    @Test
    void escortRequired() {
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", "진료", null, null,
                null)), HttpStatus.BAD_REQUEST, "동행 직원");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", "진료", "   ", null,
                null)), HttpStatus.BAD_REQUEST, "동행 직원");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", " ", "이요양", null,
                null)), HttpStatus.BAD_REQUEST, "진료 내용");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", null, "이요양", null,
                null)), HttpStatus.BAD_REQUEST, "진료 내용");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, null, "진료", "이요양", null,
                null)), HttpStatus.BAD_REQUEST, "병원");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", "진료", "가".repeat(51),
                null, null)), HttpStatus.BAD_REQUEST, "50자");
        assertRejected(() -> service.create(input(kim.getId(), DAY, null, null, "숲속내과", "가".repeat(2001), "이요양",
                null, null)), HttpStatus.BAD_REQUEST, "2000자");
        assertRejected(() -> service.create(new LabMedicalVisitDto.Input(kim.getId(), DAY, null, null, "병원", null,
                null, "진료", null, "이요양", true, "가".repeat(101), null, null, null)),
                HttpStatus.BAD_REQUEST, "100자");
        // 다른 기관 어르신
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "남어르신");
        assertRejected(() -> service.create(input(stranger.getId(), DAY, null, null, "숲속내과", "진료", "이요양",
                null, null)), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(visits.count()).isZero();
        assertThat(service.create(input(kim.getId(), DAY, null, null, "숲속내과", "진료", "이요양", null, null))
                .escortName()).isEqualTo("이요양");
    }

    // 사유·내용·처방·보호자 메모는 저장된 열이 암호문이고, 읽으면 평문으로 돌아온다
    @Test
    void textsEncryptedAtRest() {
        LabMedicalVisitDto.View saved = service.create(basic(DAY));
        em.flush();
        em.clear();
        assertEncryptedAtRest(jdbc, "lab_medical_visits", "reason", "혈압 조절 상담");
        assertEncryptedAtRest(jdbc, "lab_medical_visits", "result", "혈압 안정, 약 유지");
        assertEncryptedAtRest(jdbc, "lab_medical_visits", "prescription", "혈압약 7일분");
        assertEncryptedAtRest(jdbc, "lab_medical_visits", "guardian_note", "오후에 전화로 알림");

        LabMedicalVisitDto.View read = service.list("2026-03", null, null).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.reason()).isEqualTo("혈압 조절 상담");
        assertThat(read.result()).isEqualTo("혈압 안정, 약 유지");
        assertThat(read.prescription()).isEqualTo("혈압약 7일분");
        assertThat(read.guardianNote()).isEqualTo("오후에 전화로 알림");
        assertThat(read.guardianNotified()).isTrue();
        // 병원·진료과·동행 직원은 목록에 그대로 나가는 평문 열
        assertThat(jdbc.queryForObject("SELECT hospital FROM lab_medical_visits", String.class)).isEqualTo("숲속내과");
        assertThat(jdbc.queryForObject("SELECT escort_name FROM lab_medical_visits", String.class))
                .isEqualTo("이요양");
    }

    // 후속 확인 — 확인일이 오늘 이전이고 아직 안 한 것만. 월·어르신 필터와 함께 쓰면 모두 만족해야 한다
    @Test
    void followUpDueFilter() {
        LabMedicalVisitDto.View overdue = service.create(input(kim.getId(), LocalDate.of(2026, 1, 5), null, null,
                "숲속내과", "진료", "이요양", LocalDate.of(2026, 1, 20), false));
        service.create(input(kim.getId(), LocalDate.of(2026, 2, 5), null, null, "숲속내과", "진료", "이요양",
                LocalDate.of(2026, 2, 20), true));
        service.create(input(kim.getId(), LocalDate.of(2026, 3, 5), null, null, "숲속내과", "진료", "이요양",
                FAR_FUTURE, null));
        service.create(input(kim.getId(), LocalDate.of(2026, 3, 6), null, null, "숲속내과", "진료", "이요양", null,
                null));
        LabMedicalVisitDto.View parkOverdue = service.create(input(park.getId(), LocalDate.of(2026, 3, 7), null,
                null, "숲속내과", "진료", "이요양", LocalDate.of(2026, 3, 30), false));

        assertThat(service.list(null, null, true)).extracting(LabMedicalVisitDto.View::id)
                .containsExactly(parkOverdue.id(), overdue.id());
        assertThat(service.list(null, kim.getId(), true)).extracting(LabMedicalVisitDto.View::id)
                .containsExactly(overdue.id());
        assertThat(service.list("2026-03", null, true)).extracting(LabMedicalVisitDto.View::id)
                .containsExactly(parkOverdue.id());
        assertRejected(() -> service.list(null, null, false), HttpStatus.BAD_REQUEST, "하나를 보내");

        // 후속을 끝냈다고 고치면 목록에서 빠진다
        service.update(overdue.id(), input(kim.getId(), LocalDate.of(2026, 1, 5), null, null, "숲속내과", "진료",
                "이요양", LocalDate.of(2026, 1, 20), true));
        assertThat(service.list(null, null, true)).extracting(LabMedicalVisitDto.View::id)
                .containsExactly(parkOverdue.id());

        // 월·어르신 조회는 진료일 최신순, 어르신 이름이 채워진다
        assertThat(service.list("2026-03", null, null)).extracting(LabMedicalVisitDto.View::visitDate)
                .containsExactly(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 5));
        assertThat(service.list(null, park.getId(), null)).extracting(LabMedicalVisitDto.View::elderlyName)
                .containsExactly("박어르신");
        assertThat(service.list("2026-03", kim.getId(), null)).hasSize(2);

        // 조건이 하나도 없거나 형식이 틀리면 400, 다른 기관 어르신은 404, 다른 기관 기록은 보이지 않는다
        assertRejected(() -> service.list(null, null, null), HttpStatus.BAD_REQUEST, "하나를 보내");
        assertRejected(() -> service.list("2026-3", null, null), HttpStatus.BAD_REQUEST, "YYYY-MM");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "남어르신");
        assertRejected(() -> service.list(null, stranger.getId(), null), HttpStatus.NOT_FOUND, null);
        access.companyId = other.getId();
        assertThat(service.list(null, null, true)).isEmpty();
        assertRejected(() -> service.delete(overdue.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
    }
}
