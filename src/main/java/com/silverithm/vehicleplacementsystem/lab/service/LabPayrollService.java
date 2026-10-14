package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPayrollDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollLine;
import com.silverithm.vehicleplacementsystem.lab.entity.LabPayrollProfile;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPayrollLineRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPayrollProfileRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 직원 급여 기준과 월 급여대장.
 *
 * <p>급여는 법정 장부라 서버가 클라이언트의 계산을 믿지 않고 합을 다시 맞춰 본다:
 * 지급 합(grossPay) = 기본급·각 수당·기타 수당·식대 합, 실수령(netPay) = grossPay − 근로자 공제 합.
 * 틀리면 저장하지 않고 400. 금액은 모두 0 이상이고 급여대장의 INT 열 범위(약 21.4억)를 넘지 못한다.
 * 직원은 반드시 같은 기관 소속이어야 한다(아니면 404). 확정(CONFIRMED)된 행은 수정·삭제를 409로 막고,
 * 확정 풀기를 거쳐야 고칠 수 있다. 수정·삭제·확정은 행을 잠그고 읽어 확정과 수정이 엇갈려도
 * 확정 행이 덮어써지지 않는다.
 */
@Service
public class LabPayrollService {

    static final int MAX_EXTRA_ITEMS = 10;
    static final int LABEL = 30;
    private static final long ITEM_MAX = 1_000_000_000L;
    private static final long INT_MAX = Integer.MAX_VALUE;
    private static final int MAX_HOURS = 744; // 31일 x 24시간

    static final String LOCKED_MESSAGE = "확정된 급여입니다. 확정을 풀고 고쳐 주세요";

    static final Set<String> JOB_TYPES = Set.of("CAREGIVER", "SOCIAL_WORKER", "NURSE", "NURSE_AIDE", "THERAPIST",
            "COOK", "NUTRITIONIST", "DRIVER", "SANITATION", "OFFICE", "DIRECTOR", "OTHER");
    static final Set<String> PAY_TYPES = Set.of("MONTHLY", "HOURLY");
    private static final Set<Integer> RATES = Set.of(80, 100, 120);

    private final LabAccessService access;
    private final LabStaffService staff;
    private final LabPayrollProfileRepository profiles;
    private final LabPayrollLineRepository lines;
    private final TransactionTemplate tx;

    public LabPayrollService(LabAccessService access, LabStaffService staff, LabPayrollProfileRepository profiles,
                             LabPayrollLineRepository lines, PlatformTransactionManager txManager) {
        this.access = access;
        this.staff = staff;
        this.profiles = profiles;
        this.lines = lines;
        this.tx = new TransactionTemplate(txManager);
    }

    // ===== 급여 기준 =====

    /** 이름은 한 번에 읽어 채운다. 이름은 암호화 열이라 DB가 아니라 여기서 이름순으로 정렬한다 */
    @Transactional(readOnly = true)
    public List<LabPayrollDto.ProfileView> listProfiles() {
        Long companyId = access.requireCompanyId();
        List<LabPayrollProfile> rows = profiles.findByCompanyIdOrderByIdAsc(companyId);
        Map<Long, String> names = staff.namesOf(rows.stream().map(LabPayrollProfile::getMemberId).toList(),
                companyId);
        return rows.stream().map(p -> toView(p, names.get(p.getMemberId())))
                .sorted(Comparator.comparing(LabPayrollDto.ProfileView::memberName)
                        .thenComparing(LabPayrollDto.ProfileView::memberId))
                .toList();
    }

    /** memberId로 upsert. 직원이 같은 기관 소속이 아니면 404 */
    public LabPayrollDto.ProfileView upsertProfile(LabPayrollDto.ProfileInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("급여 기준을 보내 주세요");
        }
        Long memberId = LabValidation.required(input.memberId(), "직원");
        String jobType = LabValidation.oneOf(input.jobType(), JOB_TYPES, "직종");
        String payType = LabValidation.oneOf(input.payType(), PAY_TYPES, "급여 형태");
        int baseAmount = (int) LabValidation.longInRange(input.baseAmount(), 1, 20_000_000, "월급 또는 시급");
        BigDecimal weekly = decimalOneScale(input.weeklyHours(), "주 소정근로시간");
        if (weekly.compareTo(BigDecimal.ONE) < 0 || weekly.compareTo(BigDecimal.valueOf(52)) > 0) {
            throw LabValidation.bad("주 소정근로시간은 1~52 사이여야 합니다");
        }
        int dependents = (int) LabValidation.longInRange(toLong(input.dependents()), 1, 11, "공제대상 가족 수");
        int children = (int) LabValidation.longInRange(toLong(input.children()), 0, 10, "8~20세 자녀 수");
        if (children > dependents - 1) {
            throw LabValidation.bad("자녀 수는 본인을 뺀 공제대상 가족 수를 넘을 수 없습니다");
        }
        Integer rate = LabValidation.required(input.withholdingRate(), "원천징수 비율");
        if (!RATES.contains(rate)) {
            throw LabValidation.bad("원천징수 비율은 80, 100, 120 중 하나여야 합니다");
        }
        int meal = (int) money(input.mealAllowance(), 200_000, "식대");
        var hireDate = input.hireDate() == null ? null : LabValidation.plausibleDate(input.hireDate(), "입사일");
        boolean nps = LabValidation.required(input.npsEnrolled(), "국민연금 가입 여부");
        boolean health = LabValidation.required(input.healthEnrolled(), "건강보험 가입 여부");
        boolean employment = LabValidation.required(input.employmentEnrolled(), "고용보험 가입 여부");
        boolean senior = Boolean.TRUE.equals(input.seniorCaregiver());

        // 클라이언트가 보낸 직원 id — 다른 기관 직원이면 존재 여부를 알리지 않고 404
        String name = staff.requireAllInCompany(List.of(memberId), companyId).get(memberId);

        LabPayrollProfile saved = LabValidation.upsertWithRetry(tx, () -> {
            LabPayrollProfile profile = profiles.findByCompanyIdAndMemberId(companyId, memberId)
                    .orElseGet(() -> new LabPayrollProfile(companyId, memberId));
            profile.fill(jobType, payType, baseAmount, weekly, (short) dependents, (short) children,
                    rate.shortValue(), meal, hireDate, senior, nps, health, employment);
            return profiles.saveAndFlush(profile);
        });
        return toView(saved, name);
    }

    @Transactional
    public void deleteProfile(Long id) {
        Long companyId = access.requireCompanyId();
        profiles.delete(profiles.findByIdAndCompanyId(id, companyId).orElseThrow(
                () -> new CustomException("급여 기준을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    // ===== 월 급여대장 =====

    /** month(YYYY-MM)와 year(YYYY) 중 정확히 하나만 받는다. 월·id 순 */
    @Transactional(readOnly = true)
    public List<LabPayrollDto.LineView> listLines(String month, String year) {
        Long companyId = access.requireCompanyId();
        boolean hasMonth = month != null && !month.isBlank();
        boolean hasYear = year != null && !year.isBlank();
        if (hasMonth == hasYear) {
            throw LabValidation.bad("month 또는 year 중 하나만 보내 주세요");
        }
        String from;
        String to;
        if (hasMonth) {
            from = to = LabValidation.month(month, "월");
        } else {
            int y = LabValidation.year(year, "연도");
            from = y + "-01";
            to = y + "-12";
        }
        List<LabPayrollLine> rows = lines.findByCompanyIdAndPayMonthBetweenOrderByPayMonthAscIdAsc(companyId, from,
                to);
        Map<Long, String> names = staff.namesOf(
                rows.stream().map(LabPayrollLine::getMemberId).collect(Collectors.toSet()), companyId);
        return rows.stream().map(l -> toView(l, names.get(l.getMemberId()))).toList();
    }

    /** memberId + payMonth로 upsert. 확정된 행이면 409. 합이 맞지 않거나 음수면 400 */
    public LabPayrollDto.LineView upsertLine(LabPayrollDto.LineInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("급여 내용을 보내 주세요");
        }
        Long memberId = LabValidation.required(input.memberId(), "직원");
        String payMonth = payMonth(input.payMonth());
        Checked c = check(input);
        String name = staff.requireAllInCompany(List.of(memberId), companyId).get(memberId);

        LabPayrollLine saved = LabValidation.upsertWithRetry(tx, () -> {
            LabPayrollLine line = lines.findForUpdate(companyId, memberId, payMonth)
                    .orElseGet(() -> new LabPayrollLine(companyId, memberId, payMonth));
            if (line.isConfirmed()) {
                throw locked();
            }
            c.applyTo(line);
            return lines.saveAndFlush(line);
        });
        return toView(saved, name);
    }

    /** 그 달 DRAFT 행을 모두 확정하고 개수를 돌려준다 */
    @Transactional
    public LabPayrollDto.ConfirmResult confirm(LabPayrollDto.MonthInput input) {
        Long companyId = access.requireCompanyId();
        String month = monthOf(input);
        List<LabPayrollLine> rows = lines.findForUpdateByMonthAndStatus(companyId, month, LabPayrollLine.DRAFT);
        rows.forEach(LabPayrollLine::confirm);
        lines.saveAll(rows);
        return new LabPayrollDto.ConfirmResult(rows.size());
    }

    /** 그 달 CONFIRMED 행을 모두 DRAFT로 되돌리고 개수를 돌려준다 */
    @Transactional
    public LabPayrollDto.UnconfirmResult unconfirm(LabPayrollDto.MonthInput input) {
        Long companyId = access.requireCompanyId();
        String month = monthOf(input);
        List<LabPayrollLine> rows = lines.findForUpdateByMonthAndStatus(companyId, month, LabPayrollLine.CONFIRMED);
        rows.forEach(LabPayrollLine::reopen);
        lines.saveAll(rows);
        return new LabPayrollDto.UnconfirmResult(rows.size());
    }

    /** DRAFT만 지운다. 확정된 행이면 409 */
    @Transactional
    public void deleteLine(Long id) {
        Long companyId = access.requireCompanyId();
        LabPayrollLine line = lines.findForUpdateById(id, companyId)
                .orElseThrow(() -> new CustomException("급여 행을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        if (line.isConfirmed()) {
            throw locked();
        }
        lines.delete(line);
    }

    // ===== 검증 =====

    private static String monthOf(LabPayrollDto.MonthInput input) {
        if (input == null) {
            throw LabValidation.bad("월을 보내 주세요");
        }
        return payMonth(input.month());
    }

    private static String payMonth(String value) {
        String month = LabValidation.month(value == null ? null : value.strip(), "급여 월");
        LabValidation.year(YearMonth.parse(month).getYear(), "급여 연도");
        return month;
    }

    private static CustomException locked() {
        return new CustomException(LOCKED_MESSAGE, HttpStatus.CONFLICT);
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    /** 비면 0, 음수이거나 max를 넘으면 400 */
    private static long money(Long value, long max, String label) {
        if (value == null) {
            return 0;
        }
        return LabValidation.longInRange(value, 0, max, label);
    }

    /** 소수 첫째 자리까지만 허용하고 scale 1로 맞춘다 (DECIMAL(n,1) 열이 조용히 반올림하지 않게) */
    private static BigDecimal decimalOneScale(BigDecimal value, String label) {
        if (value == null) {
            throw LabValidation.bad(label + "을(를) 입력해 주세요");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw LabValidation.bad(label + "은(는) 소수 첫째 자리까지만 쓸 수 있습니다");
        }
        return value.setScale(1);
    }

    private static BigDecimal hours(BigDecimal value, String label) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(1);
        }
        BigDecimal h = decimalOneScale(value, label);
        if (h.signum() < 0 || h.compareTo(BigDecimal.valueOf(MAX_HOURS)) > 0) {
            throw LabValidation.bad(label + "은(는) 0~" + MAX_HOURS + " 사이여야 합니다");
        }
        return h;
    }

    /** 기타 수당 — 10개 이하, 이름 30자 이하(필수), 금액 0 이상. 받은 값을 그대로 저장하지 않고 정리해 다시 만든다 */
    private static List<LabPayrollDto.AllowanceView> allowances(List<LabPayrollDto.Allowance> input) {
        List<LabPayrollDto.AllowanceView> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        if (input.size() > MAX_EXTRA_ITEMS) {
            throw LabValidation.bad("기타 수당은 " + MAX_EXTRA_ITEMS + "개까지 쓸 수 있습니다");
        }
        for (LabPayrollDto.Allowance a : input) {
            if (a == null) {
                throw LabValidation.bad("기타 수당에 올바르지 않은 항목이 있습니다");
            }
            String label = LabValidation.requiredText(a.label(), LABEL, "기타 수당 이름");
            result.add(new LabPayrollDto.AllowanceView(label, money(a.amount(), ITEM_MAX, "기타 수당 금액"),
                    a.taxable() == null || a.taxable()));
        }
        return result;
    }

    private static List<LabPayrollDto.DeductionView> deductions(List<LabPayrollDto.Deduction> input) {
        List<LabPayrollDto.DeductionView> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        if (input.size() > MAX_EXTRA_ITEMS) {
            throw LabValidation.bad("기타 공제는 " + MAX_EXTRA_ITEMS + "개까지 쓸 수 있습니다");
        }
        for (LabPayrollDto.Deduction d : input) {
            if (d == null) {
                throw LabValidation.bad("기타 공제에 올바르지 않은 항목이 있습니다");
            }
            String label = LabValidation.requiredText(d.label(), LABEL, "기타 공제 이름");
            result.add(new LabPayrollDto.DeductionView(label, money(d.amount(), ITEM_MAX, "기타 공제 금액")));
        }
        return result;
    }

    /** 검증을 통과한 급여 값 묶음 */
    private record Checked(BigDecimal regular, BigDecimal overtime, BigDecimal night, BigDecimal holiday,
                           BigDecimal holidayOver8, int basePay, int overtimePay, int nightPay, int holidayPay,
                           int longServiceAward, int seniorAllowance, String otherAllowances, int meal, int gross,
                           int taxable, int nps, int health, int ltc, int employment, int incomeTax, int localTax,
                           String otherDeductions, int net, int employerNps, int employerHealth, int employerLtc,
                           int employerEmployment) {

        void applyTo(LabPayrollLine l) {
            l.setRegularHours(regular);
            l.setOvertimeHours(overtime);
            l.setNightHours(night);
            l.setHolidayHours(holiday);
            l.setHolidayOver8Hours(holidayOver8);
            l.setBasePay(basePay);
            l.setOvertimePay(overtimePay);
            l.setNightPay(nightPay);
            l.setHolidayPay(holidayPay);
            l.setLongServiceAward(longServiceAward);
            l.setSeniorAllowance(seniorAllowance);
            l.setOtherAllowances(otherAllowances);
            l.setMealAllowance(meal);
            l.setGrossPay(gross);
            l.setTaxablePay(taxable);
            l.setNps(nps);
            l.setHealth(health);
            l.setLtc(ltc);
            l.setEmployment(employment);
            l.setIncomeTax(incomeTax);
            l.setLocalTax(localTax);
            l.setOtherDeductions(otherDeductions);
            l.setNetPay(net);
            l.setEmployerNps(employerNps);
            l.setEmployerHealth(employerHealth);
            l.setEmployerLtc(employerLtc);
            l.setEmployerEmployment(employerEmployment);
        }
    }

    /**
     * 항목별 범위와 합을 검증한다. 합은 long으로 계산해 int 오버플로로 틀린 합이 우연히 맞는 일이 없게 하고,
     * 지급 합이 INT 열 범위를 넘으면 400이다.
     */
    private static Checked check(LabPayrollDto.LineInput in) {
        BigDecimal regular = hours(in.regularHours(), "정규 근로시간");
        BigDecimal overtime = hours(in.overtimeHours(), "연장 근로시간");
        BigDecimal night = hours(in.nightHours(), "야간 근로시간");
        BigDecimal holiday = hours(in.holidayHours(), "휴일 근로시간");
        BigDecimal holidayOver8 = hours(in.holidayOver8Hours(), "휴일 8시간 초과 근로시간");

        long basePay = money(in.basePay(), ITEM_MAX, "기본급");
        long overtimePay = money(in.overtimePay(), ITEM_MAX, "연장수당");
        long nightPay = money(in.nightPay(), ITEM_MAX, "야간수당");
        long holidayPay = money(in.holidayPay(), ITEM_MAX, "휴일수당");
        long award = money(in.longServiceAward(), ITEM_MAX, "장기근속수당");
        long senior = money(in.seniorAllowance(), ITEM_MAX, "선임 수당");
        long meal = money(in.mealAllowance(), ITEM_MAX, "식대");
        List<LabPayrollDto.AllowanceView> allowances = allowances(in.otherAllowances());
        long allowanceSum = allowances.stream().mapToLong(LabPayrollDto.AllowanceView::amount).sum();

        long gross = LabValidation.longInRange(in.grossPay(), 0, INT_MAX, "지급 합계");
        long expectedGross = basePay + overtimePay + nightPay + holidayPay + award + senior + allowanceSum + meal;
        if (expectedGross > INT_MAX) {
            throw LabValidation.bad("지급 합계가 너무 큽니다");
        }
        if (gross != expectedGross) {
            throw LabValidation.bad("지급 합계가 항목의 합과 맞지 않습니다 (항목 합 " + String.format("%,d", expectedGross)
                    + "원, 입력 " + String.format("%,d", gross) + "원)");
        }
        long taxable = LabValidation.longInRange(in.taxablePay(), 0, INT_MAX, "과세 급여");
        if (taxable > gross) {
            throw LabValidation.bad("과세 급여는 지급 합계를 넘을 수 없습니다");
        }

        long nps = money(in.nps(), ITEM_MAX, "국민연금");
        long health = money(in.health(), ITEM_MAX, "건강보험");
        long ltc = money(in.ltc(), ITEM_MAX, "장기요양보험");
        long employment = money(in.employment(), ITEM_MAX, "고용보험");
        long incomeTax = money(in.incomeTax(), ITEM_MAX, "소득세");
        long localTax = money(in.localTax(), ITEM_MAX, "지방소득세");
        List<LabPayrollDto.DeductionView> deductions = deductions(in.otherDeductions());
        long deductionSum = nps + health + ltc + employment + incomeTax + localTax
                + deductions.stream().mapToLong(LabPayrollDto.DeductionView::amount).sum();
        long expectedNet = gross - deductionSum;
        if (expectedNet < 0) {
            throw LabValidation.bad("공제 합계가 지급 합계보다 큽니다");
        }
        long net = LabValidation.longInRange(in.netPay(), 0, INT_MAX, "실수령액");
        if (net != expectedNet) {
            throw LabValidation.bad("실수령액이 지급 합계에서 공제 합계를 뺀 값과 맞지 않습니다 (계산 "
                    + String.format("%,d", expectedNet) + "원, 입력 " + String.format("%,d", net) + "원)");
        }

        return new Checked(regular, overtime, night, holiday, holidayOver8, (int) basePay, (int) overtimePay,
                (int) nightPay, (int) holidayPay, (int) award, (int) senior, LabJson.write(allowances), (int) meal,
                (int) gross, (int) taxable, (int) nps, (int) health, (int) ltc, (int) employment, (int) incomeTax,
                (int) localTax, LabJson.write(deductions), (int) net,
                (int) money(in.employerNps(), ITEM_MAX, "사업주 국민연금"),
                (int) money(in.employerHealth(), ITEM_MAX, "사업주 건강보험"),
                (int) money(in.employerLtc(), ITEM_MAX, "사업주 장기요양보험"),
                (int) money(in.employerEmployment(), ITEM_MAX, "사업주 고용보험"));
    }

    // ===== 응답 =====

    private static LabPayrollDto.ProfileView toView(LabPayrollProfile p, String memberName) {
        return new LabPayrollDto.ProfileView(p.getId(), p.getMemberId(), memberName == null ? "" : memberName,
                p.getJobType(), p.getPayType(), p.getBaseAmount(), p.getWeeklyHours(), p.getDependents(),
                p.getChildren(), p.getWithholdingRate(), p.getMealAllowance(), p.getHireDate(),
                p.isSeniorCaregiver(), p.isNpsEnrolled(), p.isHealthEnrolled(), p.isEmploymentEnrolled(),
                p.getUpdatedAt());
    }

    private static LabPayrollDto.LineView toView(LabPayrollLine l, String memberName) {
        return new LabPayrollDto.LineView(l.getId(), l.getMemberId(), memberName == null ? "" : memberName,
                l.getPayMonth(), l.getRegularHours(), l.getOvertimeHours(), l.getNightHours(), l.getHolidayHours(),
                l.getHolidayOver8Hours(), l.getBasePay(), l.getOvertimePay(), l.getNightPay(), l.getHolidayPay(),
                l.getLongServiceAward(), l.getSeniorAllowance(), LabJson.readAllowances(l.getOtherAllowances()),
                l.getMealAllowance(), l.getGrossPay(), l.getTaxablePay(), l.getNps(), l.getHealth(), l.getLtc(),
                l.getEmployment(), l.getIncomeTax(), l.getLocalTax(), LabJson.readDeductions(l.getOtherDeductions()),
                l.getNetPay(), l.getEmployerNps(), l.getEmployerHealth(), l.getEmployerLtc(),
                l.getEmployerEmployment(), l.getStatus(), l.getConfirmedAt(), l.getUpdatedAt());
    }
}
