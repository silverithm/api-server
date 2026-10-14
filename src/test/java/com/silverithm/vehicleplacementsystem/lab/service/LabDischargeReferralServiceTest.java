package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabDischargeReferralDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabElderContractDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabDischargeReferralRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabDischargeReferralServiceTest {

    private static final LocalDate DISCHARGE = LocalDate.of(2026, 5, 20);
    private static final String ALL_FOUR = "제공대장 네 칸(제공일자·방법·제공자·수령자)을 함께 적어 주세요";

    @Autowired private LabDischargeReferralService service;
    @Autowired private LabElderContractService contractService;
    @Autowired private LabDischargeReferralRepository referrals;
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

    private LabDischargeReferralDto.Input input(Long elderlyId, Long contractId, LocalDate discharge, LocalDate providedOn,
                                                String method, String provider, String recipient) {
        return new LabDischargeReferralDto.Input(elderlyId, contractId, discharge, "TRANSFER", discharge.minusDays(2),
                "3", "거동 시 보조 필요, 인지 저하 경미", "급여 제공 기록 요약: 식사·목욕·기능회복훈련", "요양원 입소 예정",
                "보호자 상담 완료", providedOn, method, provider, recipient, "박사회복지사", false);
    }

    private LabDischargeReferralDto.Input plain(LocalDate discharge) {
        return input(kim.getId(), null, discharge, null, null, null, null);
    }

    private LabElderContractDto.View contractOf(Elderly elder) {
        return contractService.create(new LabElderContractDto.Input(elder.getId(), LocalDate.of(2026, 1, 5), null, "3",
                null, LocalDate.of(2025, 12, 1), LocalDate.of(2027, 11, 30), 0, "standard", null, null, false, null, null,
                null, "ACTIVE", null, null));
    }

    @Test
    void providedFieldsAllOrNothing() {
        LocalDate day = DISCHARGE.minusDays(1);
        // 네 칸 중 일부만 있으면 400 — 어느 조합이든 같은 문구
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, day, null, null, null)),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, null, "VISIT", null, null)),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, null, null, "박사회복지사", null)),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, null, null, null, "김철수")),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, day, "VISIT", "박사회복지사", null)),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE, day, "VISIT", "박사회복지사", "  ")),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertThat(referrals.count()).isZero();

        // 넷 다 있거나 넷 다 없으면 저장
        LabDischargeReferralDto.View all = service.create(input(kim.getId(), null, DISCHARGE, day, "VISIT", "박사회복지사",
                "김철수"));
        assertThat(all.providedOn()).isEqualTo(day);
        assertThat(all.providedMethod()).isEqualTo("VISIT");
        assertThat(all.providerName()).isEqualTo("박사회복지사");
        assertThat(all.recipientName()).isEqualTo("김철수");
        LabDischargeReferralDto.View none = service.create(plain(DISCHARGE.plusDays(30)));
        assertThat(none.providedOn()).isNull();
        assertThat(none.recipientName()).isNull();

        // 수정으로 네 칸을 지우거나 일부만 남기는 것도 같은 규칙
        assertRejected(() -> service.update(all.id(), input(kim.getId(), null, DISCHARGE, day, null, "박사회복지사", "김철수")),
                HttpStatus.BAD_REQUEST, ALL_FOUR);
        assertThat(service.update(all.id(), plain(DISCHARGE)).providedOn()).isNull();
        // 허용 밖 방법
        assertRejected(() -> service.create(input(kim.getId(), null, DISCHARGE.plusDays(60), day, "PIGEON", "박", "김")),
                HttpStatus.BAD_REQUEST, "제공 방법");
        // 필수 글과 길이
        assertRejected(() -> service.create(new LabDischargeReferralDto.Input(kim.getId(), null, DISCHARGE.plusDays(61),
                "TRANSFER", DISCHARGE, "3", " ", "급여", "계획", null, null, null, null, null, null, false)),
                HttpStatus.BAD_REQUEST, "심신기능 상태");
        assertRejected(() -> service.create(new LabDischargeReferralDto.Input(kim.getId(), null, DISCHARGE.plusDays(61),
                "DEATH", DISCHARGE, "3", "상태", "급여", "계획", null, null, null, null, null, null, false)),
                HttpStatus.BAD_REQUEST, "퇴소 사유");
        assertRejected(() -> service.create(new LabDischargeReferralDto.Input(kim.getId(), null, DISCHARGE.plusDays(61),
                "SELF", DISCHARGE, "3", "상태", "급여", "가".repeat(1001), null, null, null, null, null, null, false)),
                HttpStatus.BAD_REQUEST, "1000자");
    }

    @Test
    void uniquePerElderAndDate() {
        LabDischargeReferralDto.View first = service.create(plain(DISCHARGE));

        assertRejected(() -> service.create(plain(DISCHARGE)), HttpStatus.CONFLICT, "이미 있습니다");
        assertThat(referrals.count()).isEqualTo(1);
        // 다른 날짜·다른 어르신은 별개
        LabDischargeReferralDto.View later = service.create(plain(DISCHARGE.plusDays(40)));
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        service.create(input(park.getId(), null, DISCHARGE, null, null, null, null));
        assertThat(referrals.count()).isEqualTo(3);

        // 수정으로 다른 기록과 같은 날이 되는 것은 409, 자기 날짜 그대로 고치는 것은 허용
        assertRejected(() -> service.update(later.id(), plain(DISCHARGE)), HttpStatus.CONFLICT, "이미 있습니다");
        assertThat(service.update(first.id(), plain(DISCHARGE)).id()).isEqualTo(first.id());
        assertThat(service.update(later.id(), plain(DISCHARGE.plusDays(41))).dischargeOn())
                .isEqualTo(DISCHARGE.plusDays(41));

        // 연도·어르신 필터, 퇴소일 최신순
        assertThat(service.list("2026", null)).extracting(LabDischargeReferralDto.View::dischargeOn)
                .containsExactly(DISCHARGE.plusDays(41), DISCHARGE, DISCHARGE);
        assertThat(service.list("2025", null)).isEmpty();
        assertThat(service.list(null, kim.getId())).hasSize(2);
        assertThat(service.list("2026", park.getId())).hasSize(1);
        assertThat(service.list(null, null)).hasSize(3);
        assertRejected(() -> service.list("26", null), HttpStatus.BAD_REQUEST, "YYYY");
    }

    @Test
    void contractMustBeSameCompany() {
        LabElderContractDto.View mine = contractOf(kim);
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        access.companyId = other.getId();
        LabElderContractDto.View foreign = contractOf(stranger);
        access.companyId = forest.getId();

        // 다른 기관 계약은 존재 여부도 알리지 않고 404
        assertRejected(() -> service.create(input(kim.getId(), foreign.id(), DISCHARGE, null, null, null, null)),
                HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다");
        assertThat(referrals.count()).isZero();
        // 같은 기관이어도 다른 어르신의 계약은 연결할 수 없다
        Elderly park = LabTestFixtures.elder(em, forest, "박영수");
        assertRejected(() -> service.create(input(park.getId(), mine.id(), DISCHARGE, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "다른 어르신");
        assertRejected(() -> service.create(input(kim.getId(), 999_999L, DISCHARGE, null, null, null, null)),
                HttpStatus.NOT_FOUND, null);

        LabDischargeReferralDto.View linked = service.create(input(kim.getId(), mine.id(), DISCHARGE, null, null, null,
                null));
        assertThat(linked.contractId()).isEqualTo(mine.id());
        // 수정에서 다른 기관 계약으로 바꾸는 것도 막는다
        assertRejected(() -> service.update(linked.id(), input(kim.getId(), foreign.id(), DISCHARGE, null, null, null,
                null)), HttpStatus.NOT_FOUND, "계약을 찾을 수 없습니다");
        assertThat(service.update(linked.id(), plain(DISCHARGE)).contractId()).isNull();

        // 다른 기관 어르신·다른 기관 사용자
        assertRejected(() -> service.create(input(stranger.getId(), null, DISCHARGE, null, null, null, null)),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        access.companyId = other.getId();
        assertThat(service.list(null, null)).isEmpty();
        assertRejected(() -> service.delete(linked.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void textsEncryptedAtRest() {
        LabDischargeReferralDto.View saved = service.create(input(kim.getId(), null, DISCHARGE, DISCHARGE, "VISIT",
                "박사회복지사", "김철수"));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_discharge_referrals", "functional_status", "인지 저하");
        assertEncryptedAtRest(jdbc, "lab_discharge_referrals", "services_provided", "기능회복훈련");
        assertEncryptedAtRest(jdbc, "lab_discharge_referrals", "after_plan", "요양원");
        assertEncryptedAtRest(jdbc, "lab_discharge_referrals", "counsel_note", "상담");
        assertEncryptedAtRest(jdbc, "lab_discharge_referrals", "recipient_name", "김철수");
        LabDischargeReferralDto.View read = service.list("2026", kim.getId()).get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.functionalStatus()).isEqualTo("거동 시 보조 필요, 인지 저하 경미");
        assertThat(read.afterPlan()).isEqualTo("요양원 입소 예정");
        assertThat(read.counselNote()).isEqualTo("보호자 상담 완료");
        assertThat(read.recipientName()).isEqualTo("김철수");
        assertThat(read.elderlyName()).isEqualTo("김순자");
    }
}
