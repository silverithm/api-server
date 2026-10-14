package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBathLogDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBathLogRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabBathLogServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 3, 4);

    @Autowired private LabBathLogService service;
    @Autowired private LabBathLogRepository logs;
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

    private LabBathLogDto.Input input(LocalDate day, String start, String end) {
        return new LabBathLogDto.Input(kim.getId(), day, start, end, "FULL_BATH", "이요양", "혈압 안정, 컨디션 좋음",
                "등에 발적 없음", "비누 알레르기 없음");
    }

    @Test
    void minutesComputed() {
        LabBathLogDto.View saved = service.create(input(DAY, "09:30", "10:15"));
        assertThat(saved.minutes()).isEqualTo(45);
        assertThat(saved.startTime()).isEqualTo("09:30");
        assertThat(saved.elderlyName()).isEqualTo("김순자");
        assertThat(saved.method()).isEqualTo("FULL_BATH");

        // 시각을 고치면 분도 다시 계산한다(클라이언트가 분을 보낼 방법 자체가 없다)
        assertThat(service.update(saved.id(), input(DAY, "13:05", "14:00")).minutes()).isEqualTo(55);
        assertThat(service.create(input(DAY, "00:00", "03:00")).minutes()).isEqualTo(180);
        // 같은 날 같은 어르신 두 번도 허용
        service.create(input(DAY, "15:00", "15:20"));
        assertThat(logs.count()).isEqualTo(3);

        // 월·어르신 필터와 날짜·시작 시각순
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        service.create(new LabBathLogDto.Input(park.getId(), DAY.minusDays(1), "09:00", "09:30", "BED", "이요양", null,
                null, null));
        service.create(input(LocalDate.of(2026, 4, 1), "09:00", "09:30"));
        assertThat(service.list("2026-03", null)).extracting(LabBathLogDto.View::startTime)
                .containsExactly("09:00", "00:00", "13:05", "15:00");
        assertThat(service.list("2026-03", kim.getId())).hasSize(3);
        assertThat(service.list("2026-04", park.getId())).isEmpty();
        assertRejected(() -> service.list(null, null), HttpStatus.BAD_REQUEST, "YYYY-MM");
    }

    @Test
    void endBeforeStartRejected() {
        assertRejected(() -> service.create(input(DAY, "10:00", "09:59")), HttpStatus.BAD_REQUEST, "늦어야");
        assertRejected(() -> service.create(input(DAY, "10:00", "10:00")), HttpStatus.BAD_REQUEST, "늦어야");
        // 180분 초과는 입력 실수로 본다
        assertRejected(() -> service.create(input(DAY, "09:00", "12:01")), HttpStatus.BAD_REQUEST, "180분");
        assertThat(service.create(input(DAY, "09:00", "12:00")).minutes()).isEqualTo(180);
        // 형식 오류·빈 칸
        assertRejected(() -> service.create(input(DAY, "9:00", "10:00")), HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.create(input(DAY, "09:00", null)), HttpStatus.BAD_REQUEST, "끝 시각");
        assertRejected(() -> service.create(input(DAY, "09:00", "24:00")), HttpStatus.BAD_REQUEST, "HH:MM");
        assertThat(logs.count()).isEqualTo(1);

        // 수정도 같은 규칙, 거절되면 기존 기록은 그대로
        LabBathLogDto.View saved = service.create(input(DAY, "13:00", "13:30"));
        assertRejected(() -> service.update(saved.id(), input(DAY, "14:00", "13:00")), HttpStatus.BAD_REQUEST, "늦어야");
        assertThat(service.list("2026-03", kim.getId())).extracting(LabBathLogDto.View::minutes).contains(30);
    }

    @Test
    void futureDateRejected() {
        assertRejected(() -> service.create(input(LocalDate.of(2099, 1, 1), "09:00", "09:30")),
                HttpStatus.BAD_REQUEST, "오늘보다 뒤");
        assertRejected(() -> service.create(input(null, "09:00", "09:30")), HttpStatus.BAD_REQUEST, "목욕 날짜");
        assertRejected(() -> service.create(input(LocalDate.of(1900, 1, 1), "09:00", "09:30")),
                HttpStatus.BAD_REQUEST, "2000~2100");
        assertThat(logs.count()).isZero();
        // 오늘은 허용
        assertThat(service.create(input(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), "09:00", "09:30")).id())
                .isNotNull();

        // 방법·요양보호사·길이·어르신 소속
        assertRejected(() -> service.create(new LabBathLogDto.Input(kim.getId(), DAY, "09:00", "09:30", "SHOWER", "이요양",
                null, null, null)), HttpStatus.BAD_REQUEST, "목욕 방법");
        assertRejected(() -> service.create(new LabBathLogDto.Input(kim.getId(), DAY, "09:00", "09:30", "BED", " ",
                null, null, null)), HttpStatus.BAD_REQUEST, "요양보호사");
        assertRejected(() -> service.create(new LabBathLogDto.Input(kim.getId(), DAY, "09:00", "09:30", "BED", "이요양",
                "가".repeat(101), null, null)), HttpStatus.BAD_REQUEST, "100자");
        assertRejected(() -> service.create(new LabBathLogDto.Input(kim.getId(), DAY, "09:00", "09:30", "BED", "이요양",
                null, "가".repeat(501), null)), HttpStatus.BAD_REQUEST, "500자");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.create(new LabBathLogDto.Input(stranger.getId(), DAY, "09:00", "09:30", "BED",
                "이요양", null, null, null)), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.list("2026-03", stranger.getId()), HttpStatus.NOT_FOUND, null);
        LabBathLogDto.View mine = service.create(input(DAY, "09:00", "09:30"));
        access.companyId = other.getId();
        assertRejected(() -> service.delete(mine.id()), HttpStatus.NOT_FOUND, null);
        assertThat(service.list("2026-03", null)).isEmpty();
    }

    @Test
    void notesEncryptedAtRest() {
        LabBathLogDto.View saved = service.create(input(DAY, "09:30", "10:15"));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_bath_logs", "pre_check", "혈압 안정");
        assertEncryptedAtRest(jdbc, "lab_bath_logs", "skin_note", "발적");
        assertEncryptedAtRest(jdbc, "lab_bath_logs", "note", "알레르기");
        LabBathLogDto.View read = service.list("2026-03", kim.getId()).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.preCheck()).isEqualTo("혈압 안정, 컨디션 좋음");
        assertThat(read.skinNote()).isEqualTo("등에 발적 없음");
        assertThat(read.note()).isEqualTo("비누 알레르기 없음");
    }
}
