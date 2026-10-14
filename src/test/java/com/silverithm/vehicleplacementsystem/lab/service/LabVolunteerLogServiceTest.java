package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.silverithm.vehicleplacementsystem.lab.dto.LabVolunteerLogDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabVolunteerLogRepository;

@LabJpaTest
class LabVolunteerLogServiceTest {

    @Autowired private LabVolunteerLogService service;
    @Autowired private LabVolunteerLogRepository logs;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    private LabVolunteerLogDto.Input input(LocalDate day, String name, String start, String end) {
        return new LabVolunteerLogDto.Input(day, name, "하나교회", start, end, "MEAL", "배식 도움", false, "박사회복지사");
    }

    @Test
    void minutesComputedFromTimes() {
        assertThat(service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "09:30", "11:15")).minutes())
                .isEqualTo(105);
        assertThat(service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "09:00", "09:01")).minutes()).isEqualTo(1);
        // 정확히 720분(12시간)은 허용
        LabVolunteerLogDto.View max = service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "08:00", "20:00"));
        assertThat(max.minutes()).isEqualTo(720);
        // 수정하면 분이 다시 계산된다
        LabVolunteerLogDto.View edited = service.update(max.id(), input(LocalDate.of(2026, 10, 14), "김봉사", "10:00", "10:45"));
        assertThat(edited.minutes()).isEqualTo(45);
        assertThat(edited.startTime()).isEqualTo("10:00");
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "08:00", "20:01")),
                HttpStatus.BAD_REQUEST, "720분");
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "9:00", "10:00")),
                HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "09:00", "24:00")),
                HttpStatus.BAD_REQUEST, "HH:MM");
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", null, "10:00")),
                HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "  ", "09:00", "10:00")),
                HttpStatus.BAD_REQUEST, null);
        assertThat(logs.count()).isEqualTo(3);
    }

    @Test
    void endBeforeStartRejected() {
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "11:00", "10:00")),
                HttpStatus.BAD_REQUEST, "끝 시각");
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "10:00", "10:00")),
                HttpStatus.BAD_REQUEST, "끝 시각");
        LabVolunteerLogDto.View ok = service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "10:00", "11:00"));
        assertRejected(() -> service.update(ok.id(), input(LocalDate.of(2026, 10, 14), "김봉사", "12:00", "11:00")),
                HttpStatus.BAD_REQUEST, "끝 시각");
        assertThat(service.list("2026-10", null).get(0).minutes()).isEqualTo(60);
        assertThat(logs.count()).isEqualTo(1);
    }

    @Test
    void volunteerNameEncryptedAtRest() {
        LabVolunteerLogDto.View saved = service.create(input(LocalDate.of(2026, 10, 14), "김봉사", "09:00", "10:00"));
        em.flush();
        em.clear();

        for (String column : List.of("volunteer_name", "content")) {
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM lab_volunteer_logs", String.class))
                    .as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT volunteer_name FROM lab_volunteer_logs", String.class))
                .doesNotContain("김봉사");
        LabVolunteerLogDto.View read = service.list("2026-10", null).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.volunteerName()).isEqualTo("김봉사");
        assertThat(read.content()).isEqualTo("배식 도움");

        // 평문 50자 이름이 VARCHAR(512) 안에 들어간다. 51자는 400
        service.create(input(LocalDate.of(2026, 10, 15), "가".repeat(50), "09:00", "10:00"));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT MAX(LENGTH(volunteer_name)) FROM lab_volunteer_logs", Integer.class))
                .isLessThanOrEqualTo(512);
        assertRejected(() -> service.create(input(LocalDate.of(2026, 10, 15), "가".repeat(51), "09:00", "10:00")),
                HttpStatus.BAD_REQUEST, null);
        // 다른 기관은 읽거나 지울 수 없다
        access.companyId = forest.getId() + 1000;
        assertThat(service.list("2026-10", null)).isEmpty();
        assertRejected(() -> service.delete(saved.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void yearFilter() {
        service.create(input(LocalDate.of(2025, 12, 31), "김봉사", "09:00", "10:00"));
        LabVolunteerLogDto.View a = service.create(input(LocalDate.of(2026, 1, 1), "김봉사", "13:00", "14:00"));
        LabVolunteerLogDto.View b = service.create(input(LocalDate.of(2026, 1, 1), "이봉사", "09:00", "10:00"));
        LabVolunteerLogDto.View c = service.create(input(LocalDate.of(2026, 12, 31), "김봉사", "09:00", "10:00"));
        service.create(input(LocalDate.of(2027, 1, 1), "김봉사", "09:00", "10:00"));

        // 날짜순, 같은 날은 시작 시각순
        assertThat(service.list(null, "2026")).extracting(LabVolunteerLogDto.View::id)
                .containsExactly(b.id(), a.id(), c.id());
        assertThat(service.list("2026-01", null)).hasSize(2);
        assertRejected(() -> service.list(null, null), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.list(null, "26"), HttpStatus.BAD_REQUEST, null);
    }
}
