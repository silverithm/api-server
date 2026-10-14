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

import com.silverithm.vehicleplacementsystem.lab.dto.LabCareEventDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCareEventRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@LabJpaTest
class LabCareEventServiceTest {

    private static final String PAST = "2026-01-10T09:00";

    @Autowired private LabCareEventService service;
    @Autowired private LabCareEventRepository events;
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

    private LabCareEventDto.Input input(String occurredAt, String kind, String severity, String guardianAt,
                                        String guardianMethod, String followUp, String status) {
        return new LabCareEventDto.Input(kim.getId(), occurredAt, kind, severity, "화장실", "복도에서 미끄러짐",
                "얼음찜질과 안정", guardianAt, guardianMethod, "알겠다고 하심", null, followUp, "미끄럼 방지 매트",
                status, null, "박간호사", false);
    }

    private LabCareEventDto.Input open(String kind, String severity) {
        return input(PAST, kind, severity, null, null, null, "OPEN");
    }

    private LabCareEventDto.Input at(LabCareEventDto.Input base, String occurredAt) {
        return new LabCareEventDto.Input(base.elderlyId(), occurredAt, base.kind(), base.severity(), base.place(),
                base.description(), base.immediateAction(), base.guardianNotifiedAt(), base.guardianMethod(),
                base.guardianResponse(), base.hospitalName(), base.followUp(), base.prevention(), base.status(),
                base.closedOn(), base.reporterName(), base.aiAssisted());
    }

    private void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    @Test
    void elderMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        LabCareEventDto.Input foreign = new LabCareEventDto.Input(stranger.getId(), PAST, "FALL", "LOW", null,
                "넘어짐", null, null, null, null, null, null, null, "OPEN", null, null, false);

        assertRejected(() -> service.create(foreign), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.list(null, stranger.getId(), null), HttpStatus.NOT_FOUND, null);
        assertThat(events.count()).isZero();

        LabCareEventDto.View mine = service.create(open("FALL", "LOW"));
        assertRejected(() -> service.update(mine.id(), foreign), HttpStatus.NOT_FOUND, null);
        access.companyId = other.getId();
        assertRejected(() -> service.delete(mine.id()), HttpStatus.NOT_FOUND, null);
        assertThat(service.list("2026-01", null, null)).isEmpty();
    }

    @Test
    void futureOccurredAtRejected() {
        assertRejected(() -> service.create(at(open("FALL", "LOW"), "2099-01-01T09:00")),
                HttpStatus.BAD_REQUEST, "지금보다 뒤");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        assertRejected(() -> service.create(at(open("FALL", "LOW"), now.plusMinutes(30).format(f))),
                HttpStatus.BAD_REQUEST, "지금보다 뒤");
        // 몇 분 앞선 시계 오차는 허용한다
        assertThat(service.create(at(open("FALL", "LOW"), now.plusMinutes(5).format(f))).id())
                .isNotNull();
        // 형식이 다르면(초 포함) 400
        assertRejected(() -> service.create(at(open("FALL", "LOW"), "2026-01-10T09:00:30")),
                HttpStatus.BAD_REQUEST, "YYYY-MM-DDTHH:MM");
    }

    @Test
    void guardianTimeBeforeEventRejected() {
        assertRejected(() -> service.create(input(PAST, "FALL", "LOW", "2026-01-10T08:59", "PHONE", null, "OPEN")),
                HttpStatus.BAD_REQUEST, "앞일 수 없습니다");
        // 시각과 방법은 짝
        assertRejected(() -> service.create(input(PAST, "FALL", "LOW", "2026-01-10T09:10", null, null, "OPEN")),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.create(input(PAST, "FALL", "LOW", null, "PHONE", null, "OPEN")),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.create(input(PAST, "FALL", "LOW", "2026-01-10T09:10", "FAX", null, "OPEN")),
                HttpStatus.BAD_REQUEST, null);
        // 같은 시각은 허용
        LabCareEventDto.View ok = service.create(input(PAST, "FALL", "LOW", PAST, "PHONE", null, "OPEN"));
        assertThat(ok.guardianNotifiedAt()).isEqualTo(PAST);
        assertThat(ok.occurredAt()).isEqualTo(PAST);
    }

    @Test
    void closeRequiresFollowUp() {
        assertRejected(() -> service.create(input(PAST, "BEHAVIOR", "LOW", null, null, null, "CLOSED")),
                HttpStatus.BAD_REQUEST, "후속 조치를 적어야 닫을 수 있습니다");
        assertRejected(() -> service.create(input(PAST, "BEHAVIOR", "LOW", null, null, "   ", "CLOSED")),
                HttpStatus.BAD_REQUEST, "후속 조치를 적어야 닫을 수 있습니다");
        // 열어 둔 채로는 후속 조치 없이도 저장된다
        assertThat(service.create(open("BEHAVIOR", "LOW")).status()).isEqualTo("OPEN");
        // 열린 기록을 닫으려 고칠 때도 같다
        LabCareEventDto.View saved = service.create(open("BEHAVIOR", "LOW"));
        assertRejected(() -> service.update(saved.id(), input(PAST, "BEHAVIOR", "LOW", null, null, null, "CLOSED")),
                HttpStatus.BAD_REQUEST, "후속 조치");
        assertThat(service.update(saved.id(), input(PAST, "BEHAVIOR", "LOW", null, null, "경과 관찰", "CLOSED"))
                .status()).isEqualTo("CLOSED");
    }

    @Test
    void closeRequiresGuardianForFall() {
        String need = "보호자 연락을 기록해야 닫을 수 있습니다";
        for (String kind : List.of("FALL", "INJURY", "EMERGENCY", "HOSPITAL", "MEDICATION")) {
            assertRejected(() -> service.create(input(PAST, kind, "LOW", null, null, "관찰", "CLOSED")),
                    HttpStatus.BAD_REQUEST, need);
        }
        for (String severity : List.of("HIGH", "CRITICAL")) {
            assertRejected(() -> service.create(input(PAST, "BEHAVIOR", severity, null, null, "관찰", "CLOSED")),
                    HttpStatus.BAD_REQUEST, need);
        }
        // 가벼운 상태변화·행동 변화는 보호자 연락 없이 닫을 수 있다
        assertThat(service.create(input(PAST, "CONDITION_CHANGE", "MODERATE", null, null, "관찰", "CLOSED")).status())
                .isEqualTo("CLOSED");
        // 낙상도 연락을 기록하면 닫힌다
        assertThat(service.create(input(PAST, "FALL", "LOW", "2026-01-10T09:30", "PHONE", "관찰", "CLOSED"))
                .status()).isEqualTo("CLOSED");
        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void closeFillsClosedOnAndReopenClears() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LabCareEventDto.View closed = service.create(input(PAST, "BEHAVIOR", "LOW", null, null, "경과 관찰", "CLOSED"));
        assertThat(closed.closedOn()).isEqualTo(today);

        // 이미 닫힌 기록을 닫는 날 없이 고쳐도 처음 닫은 날이 유지된다
        LabCareEventDto.Input oldDay = new LabCareEventDto.Input(kim.getId(), PAST, "BEHAVIOR", "LOW", null,
                "설명", null, null, null, null, null, "경과 관찰", null, "CLOSED", LocalDate.of(2026, 1, 12), null, false);
        service.update(closed.id(), oldDay);
        assertThat(service.update(closed.id(), input(PAST, "BEHAVIOR", "LOW", null, null, "경과 관찰 수정", "CLOSED"))
                .closedOn()).isEqualTo(LocalDate.of(2026, 1, 12));

        LabCareEventDto.View reopened = service.update(closed.id(), input(PAST, "BEHAVIOR", "LOW", null, null,
                "경과 관찰", "OPEN"));
        assertThat(reopened.status()).isEqualTo("OPEN");
        assertThat(reopened.closedOn()).isNull();

        // 닫는 날을 직접 주면 그대로 두고, 열면 그 값이 와도 비운다
        LabCareEventDto.Input withDay = new LabCareEventDto.Input(kim.getId(), PAST, "BEHAVIOR", "LOW", null,
                "설명", null, null, null, null, null, "경과 관찰", null, "CLOSED", LocalDate.of(2026, 1, 12), null, false);
        assertThat(service.update(closed.id(), withDay).closedOn()).isEqualTo(LocalDate.of(2026, 1, 12));
        LabCareEventDto.Input openWithDay = new LabCareEventDto.Input(kim.getId(), PAST, "BEHAVIOR", "LOW", null,
                "설명", null, null, null, null, null, "경과 관찰", null, "OPEN", LocalDate.of(2026, 1, 12), null, false);
        assertThat(service.update(closed.id(), openWithDay).closedOn()).isNull();
        // 상태를 안 보내면 열린 기록
        assertThat(service.update(closed.id(), input(PAST, "BEHAVIOR", "LOW", null, null, null, null)).status())
                .isEqualTo("OPEN");
    }

    @Test
    void descriptionEncryptedAtRest() {
        LabCareEventDto.View saved = service.create(input(PAST, "FALL", "HIGH", "2026-01-10T09:20", "VISIT",
                "골절 의심 병원 진료 예약", "OPEN"));
        em.flush();
        em.clear();

        for (String column : List.of("description", "immediate_action", "guardian_response", "follow_up",
                "prevention")) {
            String stored = jdbc.queryForObject("SELECT " + column + " FROM lab_care_events", String.class);
            assertThat(stored).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT description FROM lab_care_events", String.class))
                .doesNotContain("복도에서 미끄러짐");
        assertThat(jdbc.queryForObject("SELECT follow_up FROM lab_care_events", String.class))
                .doesNotContain("골절");

        LabCareEventDto.View read = service.list(null, kim.getId(), null).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.description()).isEqualTo("복도에서 미끄러짐");
        assertThat(read.followUp()).isEqualTo("골절 의심 병원 진료 예약");
        assertThat(read.elderlyName()).isEqualTo("김순자");
        // 길이 제한 — 2000자까지, 넘으면 400
        assertRejected(() -> service.create(new LabCareEventDto.Input(kim.getId(), PAST, "FALL", "LOW", null,
                "가".repeat(2001), null, null, null, null, null, null, null, "OPEN", null, null, false)),
                HttpStatus.BAD_REQUEST, "2000자");
        service.create(new LabCareEventDto.Input(kim.getId(), PAST, "FALL", "LOW", null, "가".repeat(2000), null,
                null, null, null, null, null, null, "OPEN", null, null, false));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT MAX(LENGTH(description)) FROM lab_care_events", Integer.class))
                .isGreaterThan(2000);
    }

    @Test
    void openStatusFilter() {
        LabCareEventDto.View a = service.create(input("2026-01-05T10:00", "BEHAVIOR", "LOW", null, null, null, "OPEN"));
        service.create(input("2026-01-06T10:00", "BEHAVIOR", "LOW", null, null, "관찰", "CLOSED"));
        LabCareEventDto.View c = service.create(input("2026-02-01T10:00", "BEHAVIOR", "LOW", null, null, null, "OPEN"));

        assertThat(service.list(null, null, "OPEN")).extracting(LabCareEventDto.View::id)
                .containsExactly(c.id(), a.id());
        assertThat(service.list(null, null, "CLOSED")).hasSize(1);
        // 월 경계: 1월 마지막 순간과 2월 첫 순간
        service.create(input("2026-01-31T23:59", "BEHAVIOR", "LOW", null, null, null, "OPEN"));
        assertThat(service.list("2026-01", null, null)).extracting(LabCareEventDto.View::occurredAt)
                .containsExactly("2026-01-31T23:59", "2026-01-06T10:00", "2026-01-05T10:00");
        assertThat(service.list("2026-02", null, "OPEN")).extracting(LabCareEventDto.View::id).containsExactly(c.id());
        assertThat(service.list("2026-01", kim.getId(), "OPEN")).hasSize(2);
        assertRejected(() -> service.list(null, null, null), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.list(null, null, "DONE"), HttpStatus.BAD_REQUEST, null);
    }
}
