package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabElderContractDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabElderContractRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabElderContractServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 5);

    @Autowired private LabElderContractService service;
    @Autowired private LabElderContractRepository contracts;
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

    private LabElderContractDto.Input input(Long elderlyId, LocalDate start, LocalDate end, String status,
                                            String endReason) {
        return new LabElderContractDto.Input(elderlyId, start, end, "3", "L1234567890", LocalDate.of(2025, 12, 1),
                LocalDate.of(2027, 11, 30), 0, "reduced40", 20, START, true, START.plusDays(2), START,
                START.plusDays(5), status, endReason, "보호자와 계약 2부 작성");
    }

    private LabElderContractDto.View active(Elderly elder) {
        return service.create(input(elder.getId(), START, null, "ACTIVE", null));
    }

    @Test
    void secondActiveContractRejected() {
        LabElderContractDto.View first = active(kim);

        assertRejected(() -> service.create(input(kim.getId(), START.plusMonths(1), null, "ACTIVE", null)),
                HttpStatus.CONFLICT, "이 어르신의 진행 중인 계약이 이미 있습니다");
        // status를 비우면 ACTIVE로 보므로 같다
        assertRejected(() -> service.create(input(kim.getId(), START.plusMonths(1), null, null, null)),
                HttpStatus.CONFLICT, "진행 중인 계약");
        assertThat(contracts.count()).isEqualTo(1);

        // 종료한 계약은 여러 건이어도 된다. 다른 어르신의 진행 중 계약도 별개
        service.create(input(kim.getId(), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "ENDED", "TRANSFER"));
        service.create(input(kim.getId(), LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "ENDED", "OTHER"));
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        active(park);
        assertThat(contracts.count()).isEqualTo(4);

        // 종료했던 계약을 다시 진행으로 돌리는 수정도 같은 규칙이고, 자기 자신을 수정하는 것은 막지 않는다
        LabElderContractDto.View ended = service.list("ENDED", kim.getId()).get(0);
        assertRejected(() -> service.update(ended.id(), input(kim.getId(), START, null, "ACTIVE", null)),
                HttpStatus.CONFLICT, "진행 중인 계약");
        assertThat(service.update(first.id(), input(kim.getId(), START, null, "ACTIVE", null)).id())
                .isEqualTo(first.id());
        // 전 계약을 종료하면 새 계약을 받을 수 있다
        service.update(first.id(), input(kim.getId(), START, LocalDate.of(2026, 6, 30), "ENDED", "HOSPITAL"));
        LabElderContractDto.View next = service.create(input(kim.getId(), LocalDate.of(2026, 7, 1), null, "ACTIVE", null));
        assertThat(next.status()).isEqualTo("ACTIVE");
        assertThat(service.list("ACTIVE", kim.getId())).hasSize(1);
    }

    @Test
    void endedRequiresEndDateAndReason() {
        assertRejected(() -> service.create(input(kim.getId(), START, null, "ENDED", "SELF")), HttpStatus.BAD_REQUEST,
                "종료일");
        assertRejected(() -> service.create(input(kim.getId(), START, LocalDate.of(2026, 3, 1), "ENDED", null)),
                HttpStatus.BAD_REQUEST, "종료 사유");
        assertRejected(() -> service.create(input(kim.getId(), START, LocalDate.of(2026, 3, 1), "ENDED", " ")),
                HttpStatus.BAD_REQUEST, "종료 사유");
        assertRejected(() -> service.create(input(kim.getId(), START, LocalDate.of(2026, 3, 1), "ENDED", "MOVED")),
                HttpStatus.BAD_REQUEST, "종료 사유");
        // 종료일은 계약 시작일 이후
        assertRejected(() -> service.create(input(kim.getId(), START, START.minusDays(1), "ENDED", "SELF")),
                HttpStatus.BAD_REQUEST, "앞일 수 없습니다");
        assertRejected(() -> service.create(input(kim.getId(), START, START.minusDays(1), "ACTIVE", null)),
                HttpStatus.BAD_REQUEST, "앞일 수 없습니다");
        assertRejected(() -> service.create(input(kim.getId(), START, null, "PAUSED", null)), HttpStatus.BAD_REQUEST,
                "상태");
        assertThat(contracts.count()).isZero();

        LabElderContractDto.View ended = service.create(input(kim.getId(), START, START, "ENDED", "DEATH"));
        assertThat(ended.endReason()).isEqualTo("DEATH");
        assertThat(ended.contractEnd()).isEqualTo(START);
        // 진행 중으로 되돌리면 옛 종료 사유는 비운다
        LabElderContractDto.View reopened = service.update(ended.id(), input(kim.getId(), START, null, "ACTIVE",
                "DEATH"));
        assertThat(reopened.endReason()).isNull();
        assertThat(reopened.contractEnd()).isNull();
    }

    @Test
    void validToBeforeValidFromRejected() {
        LabElderContractDto.Input base = input(kim.getId(), START, null, "ACTIVE", null);
        assertRejected(() -> service.create(withValidity(base, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31))),
                HttpStatus.BAD_REQUEST, "유효기간");
        assertRejected(() -> service.create(withValidity(base, null, LocalDate.of(2026, 12, 31))), HttpStatus.BAD_REQUEST,
                "유효기간 시작일");
        assertRejected(() -> service.create(withValidity(base, LocalDate.of(2026, 1, 1), null)), HttpStatus.BAD_REQUEST,
                "유효기간 끝나는 날");
        assertThat(contracts.count()).isZero();
        // 하루짜리 유효기간은 허용
        assertThat(service.create(withValidity(base, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))).validTo())
                .isEqualTo(LocalDate.of(2026, 1, 1));

        // 그 밖의 값 검증 — 등급·감경 구분·월 이용 일수·갱신 횟수·인정번호 길이·어르신 소속
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        assertRejected(() -> service.create(new LabElderContractDto.Input(park.getId(), START, null, "6", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 0, "standard", null, null, false, null, null, null,
                "ACTIVE", null, null)), HttpStatus.BAD_REQUEST, "장기요양등급");
        assertRejected(() -> service.create(new LabElderContractDto.Input(park.getId(), START, null, "3", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 0, "free", null, null, false, null, null, null,
                "ACTIVE", null, null)), HttpStatus.BAD_REQUEST, "본인부담");
        assertRejected(() -> service.create(new LabElderContractDto.Input(park.getId(), START, null, "3", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 0, "standard", 32, null, false, null, null, null,
                "ACTIVE", null, null)), HttpStatus.BAD_REQUEST, "월 이용 계약 일수");
        assertRejected(() -> service.create(new LabElderContractDto.Input(park.getId(), START, null, "3", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), -1, "standard", null, null, false, null, null, null,
                "ACTIVE", null, null)), HttpStatus.BAD_REQUEST, "갱신 횟수");
        assertRejected(() -> service.create(new LabElderContractDto.Input(park.getId(), START, null, "3", "1".repeat(21),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 0, "standard", null, null, false, null, null, null,
                "ACTIVE", null, null)), HttpStatus.BAD_REQUEST, "20자");
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertRejected(() -> service.create(input(stranger.getId(), START, null, "ACTIVE", null)), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
        assertThat(contracts.count()).isEqualTo(1);
    }

    private LabElderContractDto.Input withValidity(LabElderContractDto.Input b, LocalDate from, LocalDate to) {
        return new LabElderContractDto.Input(b.elderlyId(), b.contractStart(), b.contractEnd(), b.careGrade(),
                b.recognitionNo(), from, to, b.renewalCount(), b.copayClass(), b.plannedDaysPerMonth(), b.signedOn(),
                b.copyGiven(), b.contractNotifiedOn(), b.planNotifiedOn(), b.rightsGuidedOn(), b.status(),
                b.endReason(), b.note());
    }

    @Test
    void recognitionNoEncryptedAtRest() {
        LabElderContractDto.View saved = active(kim);
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_elder_contracts", "recognition_no", "L1234567890");
        assertEncryptedAtRest(jdbc, "lab_elder_contracts", "note", "계약 2부");
        LabElderContractDto.View read = service.list(null, kim.getId()).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.recognitionNo()).isEqualTo("L1234567890");
        assertThat(read.note()).isEqualTo("보호자와 계약 2부 작성");
        assertThat(read.careGrade()).isEqualTo("3");
        assertThat(read.copayClass()).isEqualTo("reduced40");
        assertThat(read.plannedDaysPerMonth()).isEqualTo(20);
        assertThat(read.copyGiven()).isTrue();
        assertThat(read.planNotifiedOn()).isEqualTo(START);
        assertThat(read.elderlyName()).isEqualTo("김순자");
        // 인정번호를 비우면 null로 저장되고 암호문도 없다
        LabElderContractDto.Input blank = input(kim.getId(), START, null, "ACTIVE", null);
        LabElderContractDto.View cleared = service.update(saved.id(), new LabElderContractDto.Input(blank.elderlyId(),
                blank.contractStart(), blank.contractEnd(), blank.careGrade(), "  ", blank.validFrom(), blank.validTo(),
                blank.renewalCount(), blank.copayClass(), null, null, null, null, null, null, "ACTIVE", null, null));
        assertThat(cleared.recognitionNo()).isNull();
        assertThat(cleared.plannedDaysPerMonth()).isNull();
        assertThat(cleared.copyGiven()).isFalse();
    }

    @Test
    void statusFilter() {
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        LabElderContractDto.View kimActive = active(kim);
        LabElderContractDto.View parkActive = active(park);
        LabElderContractDto.View kimEnded = service.create(input(kim.getId(), LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31), "ENDED", "SELF"));

        assertThat(service.list("ACTIVE", null)).extracting(LabElderContractDto.View::id)
                .containsExactlyInAnyOrder(kimActive.id(), parkActive.id());
        assertThat(service.list("ENDED", null)).extracting(LabElderContractDto.View::id).containsExactly(kimEnded.id());
        assertThat(service.list(null, null)).hasSize(3);
        // 계약 시작일 최신순
        assertThat(service.list(null, kim.getId())).extracting(LabElderContractDto.View::id)
                .containsExactly(kimActive.id(), kimEnded.id());
        assertThat(service.list("ACTIVE", kim.getId())).extracting(LabElderContractDto.View::id)
                .containsExactly(kimActive.id());
        assertThat(service.list("", park.getId())).hasSize(1);
        assertRejected(() -> service.list("DONE", null), HttpStatus.BAD_REQUEST, "상태");

        // 다른 기관에서는 보이지도 지워지지도 않는다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.list(null, null)).isEmpty();
        assertRejected(() -> service.list(null, kim.getId()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.delete(kimActive.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.delete(kimEnded.id());
        assertThat(contracts.count()).isEqualTo(2);
    }
}
