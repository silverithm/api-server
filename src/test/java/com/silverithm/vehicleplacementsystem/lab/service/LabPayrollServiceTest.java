package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPayrollDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPayrollLineRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPayrollProfileRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabPayrollServiceTest {

    @Autowired private LabPayrollService service;
    @Autowired private LabPayrollProfileRepository profiles;
    @Autowired private LabPayrollLineRepository lines;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member lee;
    private Member han;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        lee = LabTestFixtures.member(em, forest, "이요양", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        han = LabTestFixtures.member(em, forest, "한사무", Member.Role.OFFICE, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private LabPayrollDto.ProfileInput profile(Long memberId) {
        return new LabPayrollDto.ProfileInput(memberId, "CAREGIVER", "MONTHLY", 2_500_000L, new BigDecimal("40"), 3,
                1, 100, 200_000L, LocalDate.of(2022, 3, 1), false, true, true, true);
    }

    /** 합이 맞는 기본 급여 한 줄: 지급 2,500,000+100,000+200,000 / 공제 135,000+1,000 */
    private LabPayrollDto.LineInput line(Long memberId, String month) {
        return new LabPayrollDto.LineInput(memberId, month, new BigDecimal("209"), null, null, null, null,
                2_500_000L, 0L, 0L, 0L, 0L, 0L,
                List.of(new LabPayrollDto.Allowance("직책수당", 100_000L, true)), 200_000L, 2_800_000L, 2_600_000L,
                100_000L, 90_000L, 10_000L, 20_000L, 5_000L, 500L,
                List.of(new LabPayrollDto.Deduction("조합비", 1_000L)), 2_573_500L,
                100_000L, 90_000L, 10_000L, 25_000L);
    }

    private LabPayrollDto.LineInput withNet(LabPayrollDto.LineInput i, Long gross, Long net) {
        return new LabPayrollDto.LineInput(i.memberId(), i.payMonth(), i.regularHours(), i.overtimeHours(),
                i.nightHours(), i.holidayHours(), i.holidayOver8Hours(), i.basePay(), i.overtimePay(), i.nightPay(),
                i.holidayPay(), i.longServiceAward(), i.seniorAllowance(), i.otherAllowances(), i.mealAllowance(),
                gross, i.taxablePay(), i.nps(), i.health(), i.ltc(), i.employment(), i.incomeTax(), i.localTax(),
                i.otherDeductions(), net, i.employerNps(), i.employerHealth(), i.employerLtc(),
                i.employerEmployment());
    }

    // 급여 기준의 직원은 같은 기관 직원이어야 한다 — 아니면 404, 같은 직원으로 다시 저장하면 한 건으로 합쳐진다
    @Test
    void profileMemberMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member stranger = LabTestFixtures.member(em, other, "남직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);

        assertStatus(() -> service.upsertProfile(profile(stranger.getId())), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.upsertProfile(profile(999_999L)), HttpStatus.NOT_FOUND);
        assertThat(profiles.count()).isZero();
        assertThatThrownBy(() -> service.upsertProfile(profile(stranger.getId())))
                .hasMessage("직원을 찾을 수 없습니다");

        LabPayrollDto.ProfileView saved = service.upsertProfile(profile(lee.getId()));
        assertThat(saved.memberName()).isEqualTo("이요양");
        assertThat(saved.baseAmount()).isEqualTo(2_500_000);
        LabPayrollDto.ProfileView again = service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(),
                "NURSE", "HOURLY", 12_000L, new BigDecimal("20.5"), 1, 0, 80, 0L, null, true, false, true, false));
        assertThat(again.id()).isEqualTo(saved.id());
        assertThat(again.jobType()).isEqualTo("NURSE");
        assertThat(again.weeklyHours()).isEqualByComparingTo("20.5");
        assertThat(profiles.count()).isEqualTo(1);

        // 목록은 이름을 채운다. 다른 기관 요청자는 이 기준을 보지도 지우지도 못한다
        service.upsertProfile(profile(han.getId()));
        assertThat(service.listProfiles()).extracting(LabPayrollDto.ProfileView::memberName)
                .containsExactly("이요양", "한사무");
        access.companyId = other.getId();
        assertThat(service.listProfiles()).isEmpty();
        assertStatus(() -> service.deleteProfile(saved.id()), HttpStatus.NOT_FOUND);
        access.companyId = forest.getId();
        service.deleteProfile(saved.id());
        assertThat(profiles.count()).isEqualTo(1);

        // 값 범위
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                20_000_001L, new BigDecimal("40"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                0L, new BigDecimal("40"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("53"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40.55"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 12, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 1, 11, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 2, 2, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 1, 0, 90, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 1, 0, 100, 200_001L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "BOSS", "MONTHLY",
                1L, new BigDecimal("40"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "DAILY",
                1L, new BigDecimal("40"), 1, 0, 100, 0L, null, false, true, true, true)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertProfile(new LabPayrollDto.ProfileInput(lee.getId(), "CAREGIVER", "MONTHLY",
                1L, new BigDecimal("40"), 1, 0, 100, 0L, null, false, null, true, true)), HttpStatus.BAD_REQUEST);
    }

    // 서버가 지급·공제 합을 다시 맞춰 본다 — 틀린 합·음수·범위 초과는 400, 직원은 같은 기관만
    @Test
    void lineSumsValidated() {
        LabPayrollDto.LineView ok = service.upsertLine(line(lee.getId(), "2026-03"));
        assertThat(ok.memberName()).isEqualTo("이요양");
        assertThat(ok.status()).isEqualTo("DRAFT");
        assertThat(ok.grossPay()).isEqualTo(2_800_000);
        assertThat(ok.netPay()).isEqualTo(2_573_500);
        assertThat(ok.otherAllowances()).extracting(LabPayrollDto.AllowanceView::label).containsExactly("직책수당");
        assertThat(ok.otherDeductions()).extracting(LabPayrollDto.DeductionView::amount).containsExactly(1_000L);
        assertThat(ok.regularHours()).isEqualByComparingTo("209");

        // 지급 합이 틀리면 400 (항목 합은 2,800,000)
        assertStatus(() -> service.upsertLine(withNet(line(lee.getId(), "2026-04"), 2_800_001L, 2_573_501L)),
                HttpStatus.BAD_REQUEST);
        // 실수령이 틀리면 400
        assertStatus(() -> service.upsertLine(withNet(line(lee.getId(), "2026-04"), 2_800_000L, 2_573_499L)),
                HttpStatus.BAD_REQUEST);
        assertThat(lines.count()).isEqualTo(1);

        // 음수 항목, 월 형식, 시간 범위, 기타 항목 개수·이름 길이
        LabPayrollDto.LineInput base = line(lee.getId(), "2026-04");
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, -1L, 0L, 0L, 0L, 0L, 0L, null, 0L, -1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, null, -1L, 0L, 0L,
                0L, 0L)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(line(lee.getId(), "2026-13")), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(line(lee.getId(), "202604")), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04",
                new BigDecimal("745"), null, null, null, null, 0L, 0L, 0L, 0L, 0L, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, null, 0L, 0L, 0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        List<LabPayrollDto.Allowance> eleven = java.util.Collections.nCopies(11,
                new LabPayrollDto.Allowance("수당", 1L, true));
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 0L, 0L, 0L, 0L, 0L, 0L, eleven, 0L, 11L, 11L, 0L, 0L, 0L, 0L, 0L, 0L, null, 11L, 0L, 0L,
                0L, 0L)), HttpStatus.BAD_REQUEST);
        String long31 = "가".repeat(31);
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 0L, 0L, 0L, 0L, 0L, 0L, List.of(new LabPayrollDto.Allowance(long31, 1L, true)), 0L, 1L,
                1L, 0L, 0L, 0L, 0L, 0L, 0L, null, 1L, 0L, 0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 0L, 0L, 0L, 0L, 0L, 0L, null, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                java.util.Collections.nCopies(11, new LabPayrollDto.Deduction("공제", 0L)), 0L, 0L, 0L, 0L, 0L)),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 0L, 0L, 0L, 0L, 0L, 0L, List.of(new LabPayrollDto.Allowance("  ", 1L, true)), 0L, 1L, 1L,
                0L, 0L, 0L, 0L, 0L, 0L, null, 1L, 0L, 0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        // 공제가 지급보다 크면 400 (실수령이 음수가 된다)
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 100L, 0L, 0L, 0L, 0L, 0L, null, 0L, 100L, 100L, 200L, 0L, 0L, 0L, 0L, 0L, null, 0L, 0L,
                0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        // 과세 급여가 지급 합보다 크면 400
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 100L, 0L, 0L, 0L, 0L, 0L, null, 0L, 100L, 101L, 0L, 0L, 0L, 0L, 0L, 0L, null, 100L, 0L,
                0L, 0L, 0L)), HttpStatus.BAD_REQUEST);

        // int 범위를 넘는 합은 항목이 각각 범위 안이어도 거절 (long으로 더해 오버플로로 우연히 맞는 일이 없다)
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 1_000_000_000L, 1_000_000_000L, 1_000_000_000L, 0L, 0L, 0L, null, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, null, 0L, 0L, 0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.upsertLine(new LabPayrollDto.LineInput(lee.getId(), "2026-04", null, null, null,
                null, null, 1_000_000_001L, 0L, 0L, 0L, 0L, 0L, null, 0L, 1_000_000_001L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, null, 1_000_000_001L, 0L, 0L, 0L, 0L)), HttpStatus.BAD_REQUEST);
        assertThat(lines.count()).isEqualTo(1);

        // 직원이 다른 기관이면 404, 같은 직원·월은 한 행으로 합쳐진다(upsert)
        Company other = LabTestFixtures.company(em, "다른기관");
        Member stranger = LabTestFixtures.member(em, other, "남직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        assertStatus(() -> service.upsertLine(line(stranger.getId(), "2026-03")), HttpStatus.NOT_FOUND);
        LabPayrollDto.LineView again = service.upsertLine(withNet(base, 2_800_000L, 2_573_500L));
        LabPayrollDto.LineView sameMonth = service.upsertLine(line(lee.getId(), "2026-03"));
        assertThat(sameMonth.id()).isEqualTo(ok.id());
        assertThat(again.id()).isNotEqualTo(ok.id());
        assertThat(lines.count()).isEqualTo(2);
        // 값이 없는 선택 항목은 0으로 저장된다
        LabPayrollDto.LineView zero = service.upsertLine(new LabPayrollDto.LineInput(han.getId(), "2026-03", null,
                null, null, null, null, null, null, null, null, null, null, null, null, 0L, 0L, null, null, null,
                null, null, null, null, 0L, null, null, null, null));
        assertThat(zero.grossPay()).isZero();
        assertThat(zero.otherAllowances()).isEmpty();
    }

    // 확정된 행은 수정·삭제가 409이고 메시지는 계약 그대로
    @Test
    void confirmedLineCannotChange() {
        LabPayrollDto.LineView draft = service.upsertLine(line(lee.getId(), "2026-03"));
        service.upsertLine(line(han.getId(), "2026-03"));
        service.upsertLine(line(lee.getId(), "2026-04"));

        assertThat(service.confirm(new LabPayrollDto.MonthInput("2026-03")).confirmed()).isEqualTo(2);
        // 이미 확정된 달에 다시 확정해도 0건
        assertThat(service.confirm(new LabPayrollDto.MonthInput("2026-03")).confirmed()).isZero();

        assertThatThrownBy(() -> service.upsertLine(line(lee.getId(), "2026-03")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("확정된 급여입니다. 확정을 풀고 고쳐 주세요");
                });
        assertStatus(() -> service.deleteLine(draft.id()), HttpStatus.CONFLICT);
        assertThat(lines.count()).isEqualTo(3);
        // 확정되지 않은 다른 달은 그대로 수정·삭제된다
        LabPayrollDto.LineView april = service.listLines("2026-04", null).get(0);
        assertThat(april.status()).isEqualTo("DRAFT");
        service.deleteLine(april.id());
        assertThat(lines.count()).isEqualTo(2);

        List<LabPayrollDto.LineView> march = service.listLines("2026-03", null);
        assertThat(march).allSatisfy(l -> {
            assertThat(l.status()).isEqualTo("CONFIRMED");
            assertThat(l.confirmedAt()).isNotNull();
        });
        // 확정 뒤에도 값은 그대로
        assertThat(march.get(0).grossPay()).isEqualTo(2_800_000);

        // 다른 기관은 확정에도 삭제에도 손댈 수 없다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.confirm(new LabPayrollDto.MonthInput("2026-04")).confirmed()).isZero();
        assertStatus(() -> service.deleteLine(draft.id()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.confirm(new LabPayrollDto.MonthInput("2026-3")), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.confirm(null), HttpStatus.BAD_REQUEST);
    }

    // 확정 풀기는 그 달 확정 행만 DRAFT로 되돌려 다시 고칠 수 있게 하고 개수를 돌려준다
    @Test
    void unconfirmReopensMonth() {
        service.upsertLine(line(lee.getId(), "2026-03"));
        service.upsertLine(line(han.getId(), "2026-03"));
        service.upsertLine(line(lee.getId(), "2026-04"));
        service.confirm(new LabPayrollDto.MonthInput("2026-03"));
        service.confirm(new LabPayrollDto.MonthInput("2026-04"));

        assertThat(service.unconfirm(new LabPayrollDto.MonthInput("2026-03")).unconfirmed()).isEqualTo(2);
        assertThat(service.unconfirm(new LabPayrollDto.MonthInput("2026-03")).unconfirmed()).isZero();
        assertThat(service.listLines("2026-03", null)).allSatisfy(l -> {
            assertThat(l.status()).isEqualTo("DRAFT");
            assertThat(l.confirmedAt()).isNull();
        });
        // 다른 달은 확정 그대로
        assertThat(service.listLines("2026-04", null).get(0).status()).isEqualTo("CONFIRMED");

        // 풀린 달은 다시 수정·삭제된다
        LabPayrollDto.LineInput changed = new LabPayrollDto.LineInput(lee.getId(), "2026-03", null, null, null, null,
                null, 3_000_000L, 0L, 0L, 0L, 0L, 0L, null, 0L, 3_000_000L, 3_000_000L, 0L, 0L, 0L, 0L, 0L, 0L, null,
                3_000_000L, 0L, 0L, 0L, 0L);
        assertThat(service.upsertLine(changed).grossPay()).isEqualTo(3_000_000);
        service.deleteLine(service.listLines("2026-03", null).get(1).id());
        assertThat(service.listLines("2026-03", null)).hasSize(1);
    }

    // 월 또는 연도 중 하나만 받고, 연도는 1월~12월 경계를 지킨다
    @Test
    void yearFilter() {
        for (String month : List.of("2025-12", "2026-01", "2026-06", "2026-12", "2027-01")) {
            service.upsertLine(line(lee.getId(), month));
        }
        assertThat(service.listLines(null, "2026")).extracting(LabPayrollDto.LineView::payMonth)
                .containsExactly("2026-01", "2026-06", "2026-12");
        assertThat(service.listLines("2026-06", null)).hasSize(1);
        assertThat(service.listLines("2025-11", null)).isEmpty();

        assertStatus(() -> service.listLines(null, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listLines("2026-06", "2026"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listLines(null, "26"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listLines("2026-6", null), HttpStatus.BAD_REQUEST);

        // 다른 기관은 이 급여를 보지 못한다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertThat(service.listLines(null, "2026")).isEmpty();
    }
}
