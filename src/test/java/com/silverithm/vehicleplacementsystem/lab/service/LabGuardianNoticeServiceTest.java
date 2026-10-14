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

import com.silverithm.vehicleplacementsystem.lab.dto.LabGuardianNoticeDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabGuardianNoticeRepository;

@LabJpaTest
class LabGuardianNoticeServiceTest {

    @Autowired private LabGuardianNoticeService service;
    @Autowired private LabGuardianNoticeRepository notices;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
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

    private LabGuardianNoticeDto.Input input(Elderly elder, String month, String body) {
        return new LabGuardianNoticeDto.Input(elder.getId(), month, "10월 소식", body,
                "{\"attendanceDays\":18}", "박사회복지사", true);
    }

    @Test
    void upsertByElderAndMonth() {
        LabGuardianNoticeDto.View first = service.upsert(input(kim, "2026-10", "처음 초안"));
        assertThat(first.status()).isEqualTo("DRAFT");
        LabGuardianNoticeDto.View second = service.upsert(input(kim, "2026-10", "고친 초안"));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.body()).isEqualTo("고친 초안");
        assertThat(notices.count()).isEqualTo(1);

        service.upsert(input(kim, "2026-11", "다른 달"));
        service.upsert(input(park, "2026-10", "다른 어르신"));
        assertThat(notices.count()).isEqualTo(3);
        List<LabGuardianNoticeDto.View> october = service.listByMonth("2026-10");
        assertThat(october).extracting(LabGuardianNoticeDto.View::elderlyName).containsExactly("김순자", "박영수");

        assertRejected(() -> service.upsert(input(kim, "2026-13", "x")), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(kim, "2026-10", "  ")), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(kim, "2026-10", "가".repeat(4001))), HttpStatus.BAD_REQUEST, "4000자");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.upsert(input(stranger, "2026-10", "x")), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
        assertThat(notices.count()).isEqualTo(3);
    }

    @Test
    void sentRequiresDateAndMethod() {
        LabGuardianNoticeDto.View draft = service.upsert(input(kim, "2026-10", "본문"));
        assertRejected(() -> service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(null, "SMS", null)),
                HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(
                LocalDate.of(2026, 11, 2), null, null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(
                LocalDate.of(2026, 11, 2), "PIGEON", null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.markSent(draft.id(), null), HttpStatus.BAD_REQUEST, null);
        assertThat(notices.findById(draft.id()).orElseThrow().getStatus()).isEqualTo("DRAFT");

        LabGuardianNoticeDto.View sent = service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(
                LocalDate.of(2026, 11, 2), "SMS", "김철수"));
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.sentOn()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(sent.method()).isEqualTo("SMS");
        assertThat(sent.recipient()).isEqualTo("김철수");
        assertRejected(() -> service.markSent(9999L, new LabGuardianNoticeDto.SentInput(
                LocalDate.of(2026, 11, 2), "SMS", null)), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void sentNoticeCannotBeEdited() {
        LabGuardianNoticeDto.View draft = service.upsert(input(kim, "2026-10", "본문"));
        service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(LocalDate.of(2026, 11, 2), "KAKAO", null));

        assertRejected(() -> service.upsert(input(kim, "2026-10", "몰래 고침")), HttpStatus.CONFLICT,
                "보낸 안내입니다. 보냄 표시를 풀고 고쳐 주세요");
        assertThat(service.listByMonth("2026-10").get(0).body()).isEqualTo("본문");
        // 다른 달·다른 어르신은 영향받지 않는다
        assertThat(service.upsert(input(kim, "2026-11", "다음 달")).status()).isEqualTo("DRAFT");
    }

    @Test
    void unsentReopens() {
        LabGuardianNoticeDto.View draft = service.upsert(input(kim, "2026-10", "본문"));
        service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(LocalDate.of(2026, 11, 2), "SMS", "김철수"));

        LabGuardianNoticeDto.View reopened = service.markUnsent(draft.id());
        assertThat(reopened.status()).isEqualTo("DRAFT");
        assertThat(reopened.sentOn()).isNull();
        assertThat(reopened.method()).isNull();
        assertThat(reopened.recipient()).isNull();

        assertThat(service.upsert(input(kim, "2026-10", "고친 본문")).body()).isEqualTo("고친 본문");
        assertThat(notices.count()).isEqualTo(1);

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.markUnsent(draft.id()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.delete(draft.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.delete(draft.id());
        assertThat(notices.count()).isZero();
    }

    @Test
    void bodyAndRecipientEncryptedAtRest() {
        LabGuardianNoticeDto.View draft = service.upsert(input(kim, "2026-10", "김순자 어르신은 10월에 18일 출석하셨습니다"));
        service.markSent(draft.id(), new LabGuardianNoticeDto.SentInput(LocalDate.of(2026, 11, 2), "SMS", "김철수"));
        em.flush();
        em.clear();

        for (String column : List.of("body", "facts", "recipient")) {
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM lab_guardian_notices", String.class))
                    .as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT body FROM lab_guardian_notices", String.class)).doesNotContain("출석");
        assertThat(jdbc.queryForObject("SELECT recipient FROM lab_guardian_notices", String.class))
                .doesNotContain("김철수");
        LabGuardianNoticeDto.View read = service.listByMonth("2026-10").get(0);
        assertThat(read.body()).isEqualTo("김순자 어르신은 10월에 18일 출석하셨습니다");
        assertThat(read.recipient()).isEqualTo("김철수");
        assertThat(read.facts()).isEqualTo("{\"attendanceDays\":18}");

        // 받는 사람 평문 50자는 암호문으로 VARCHAR(512)에 들어간다
        LabGuardianNoticeDto.View longest = service.upsert(input(park, "2026-10", "본문"));
        service.markSent(longest.id(), new LabGuardianNoticeDto.SentInput(LocalDate.of(2026, 11, 2), "SMS",
                "가".repeat(50)));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT MAX(LENGTH(recipient)) FROM lab_guardian_notices", Integer.class))
                .isLessThanOrEqualTo(512);
        assertRejected(() -> service.markSent(longest.id(), new LabGuardianNoticeDto.SentInput(
                LocalDate.of(2026, 11, 2), "SMS", "가".repeat(51))), HttpStatus.BAD_REQUEST, null);
    }
}
