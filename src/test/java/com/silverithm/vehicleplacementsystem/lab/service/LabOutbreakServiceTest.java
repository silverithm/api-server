package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabOutbreakDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabOutbreakRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabOutbreakServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 2, 10);

    @Autowired private LabOutbreakService service;
    @Autowired private LabOutbreakRepository outbreaks;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private LabOutbreakDto.Input input(String kind, String alert, LocalDate guardian, LocalDate center,
                                       LocalDate authority, LocalDate ended, List<String> actions) {
        return new LabOutbreakDto.Input(kind, "독감", START, 3, 1, guardian, center, authority, alert, actions, ended,
                "격리실 운영");
    }

    private LabOutbreakDto.Input outbreak(LocalDate guardian, LocalDate center, LocalDate authority, LocalDate ended) {
        return input("OUTBREAK", null, guardian, center, authority, ended, List.of("VISIT_LIMIT"));
    }

    // 보호자 안내·보건소 신고·지자체 보고·종료일은 시작일보다 앞설 수 없다. 같은 날은 된다
    @Test
    void datesNotBeforeStart() {
        LocalDate before = START.minusDays(1);
        assertRejected(() -> service.create(outbreak(before, null, null, null)), HttpStatus.BAD_REQUEST, "보호자 안내일");
        assertRejected(() -> service.create(outbreak(null, before, null, null)), HttpStatus.BAD_REQUEST, "보건소 신고일");
        assertRejected(() -> service.create(outbreak(null, null, before, null)), HttpStatus.BAD_REQUEST,
                "지자체·공단 보고일");
        assertRejected(() -> service.create(outbreak(null, null, null, before)), HttpStatus.BAD_REQUEST, "종료일");
        assertThat(outbreaks.count()).isZero();

        LabOutbreakDto.View saved = service.create(outbreak(START, START.plusDays(1), START.plusDays(1),
                START.plusDays(10)));
        assertThat(saved.endedOn()).isEqualTo(START.plusDays(10));
        assertThat(saved.writerName()).isEqualTo("숲속 관리자");
        assertThat(saved.note()).isEqualTo("격리실 운영");
        assertEncryptedAtRest(jdbc, "lab_outbreaks", "note", "격리실 운영");
        // 고쳐 쓸 때도 같은 규칙
        assertRejected(() -> service.update(saved.id(), outbreak(null, null, null, before)), HttpStatus.BAD_REQUEST,
                "종료일");

        // 다른 기관은 보지도 고치지도 지우지도 못한다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.list("2026")).isEmpty();
        assertRejected(() -> service.update(saved.id(), outbreak(null, null, null, null)), HttpStatus.NOT_FOUND,
                "찾을 수 없습니다");
        assertRejected(() -> service.delete(saved.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();
        assertThat(service.list("2026")).hasSize(1);
        service.delete(saved.id());
        assertThat(outbreaks.count()).isZero();
    }

    // 국가 위기 경보는 경보 단계가 필수, 그 밖의 종류는 단계를 비운다
    @Test
    void nationalRequiresAlertLevel() {
        assertRejected(() -> service.create(input("NATIONAL", null, null, null, null, null, List.of())),
                HttpStatus.BAD_REQUEST, "경보 단계");
        assertRejected(() -> service.create(input("NATIONAL", "PURPLE", null, null, null, null, List.of())),
                HttpStatus.BAD_REQUEST, "경보 단계");
        LabOutbreakDto.View national = service.create(input("NATIONAL", "ORANGE", null, null, null, null,
                List.of()));
        assertThat(national.alertLevel()).isEqualTo("ORANGE");
        LabOutbreakDto.View local = service.create(input("CASE", "RED", null, null, null, null, List.of()));
        assertThat(local.alertLevel()).isNull();
        assertRejected(() -> service.create(input("FLU", null, null, null, null, null, List.of())),
                HttpStatus.BAD_REQUEST, "종류");
    }

    // 조치는 정한 키만, 중복 없이. 저장은 정해진 순서
    @Test
    void unknownActionRejected() {
        assertRejected(() -> service.create(input("CASE", null, null, null, null, null, List.of("MAGIC"))),
                HttpStatus.BAD_REQUEST, "알 수 없는 조치");
        assertRejected(() -> service.create(input("CASE", null, null, null, null, null,
                java.util.Arrays.asList("VISIT_LIMIT", null))), HttpStatus.BAD_REQUEST, "알 수 없는 조치");
        assertRejected(() -> service.create(input("CASE", null, null, null, null, null,
                List.of("VISIT_LIMIT", "VISIT_LIMIT"))), HttpStatus.BAD_REQUEST, "두 번");
        assertThat(outbreaks.count()).isZero();
        LabOutbreakDto.View ok = service.create(input("CASE", null, null, null, null, null,
                List.of("ACTIVITY_STOP", "VISIT_LIMIT")));
        assertThat(ok.actions()).containsExactly("VISIT_LIMIT", "ACTIVITY_STOP");
    }

    // 모든 날짜는 오늘 이후일 수 없고, 환자 수는 0~500
    @Test
    void futureDateRejected() {
        LocalDate future = LabValidation.seoulToday().plusDays(1);
        assertRejected(() -> service.create(new LabOutbreakDto.Input("CASE", "독감", future, 0, 0, null, null, null,
                null, List.of(), null, null)), HttpStatus.BAD_REQUEST, "시작일");
        assertRejected(() -> service.create(outbreak(future, null, null, null)), HttpStatus.BAD_REQUEST, "보호자 안내일");
        assertRejected(() -> service.create(outbreak(null, future, null, null)), HttpStatus.BAD_REQUEST, "보건소 신고일");
        assertRejected(() -> service.create(outbreak(null, null, future, null)), HttpStatus.BAD_REQUEST,
                "지자체·공단 보고일");
        assertRejected(() -> service.create(outbreak(null, null, null, future)), HttpStatus.BAD_REQUEST, "종료일");
        assertRejected(() -> service.create(new LabOutbreakDto.Input("CASE", "독감", START, 501, 0, null, null, null,
                null, List.of(), null, null)), HttpStatus.BAD_REQUEST, "환자 수");
        assertRejected(() -> service.create(new LabOutbreakDto.Input("CASE", " ", START, 0, 0, null, null, null,
                null, List.of(), null, null)), HttpStatus.BAD_REQUEST, "병명");
        assertThat(outbreaks.count()).isZero();
    }
}
