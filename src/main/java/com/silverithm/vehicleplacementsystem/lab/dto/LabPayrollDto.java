package com.silverithm.vehicleplacementsystem.lab.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 급여 기준·월 급여대장 요청·응답.
 *
 * <p>금액 입력은 Long으로 받는다 — int 범위를 넘는 값이 JSON 파싱 단계에서 500/모호한 오류가 되지 않고
 * 서비스가 400으로 거절하게 하기 위해서다. 요청에는 기관 id가 없다(서버가 로그인 정보로 정한다).
 */
public final class LabPayrollDto {

    private LabPayrollDto() {
    }

    public record ProfileInput(Long memberId, String jobType, String payType, Long baseAmount,
                               BigDecimal weeklyHours, Integer dependents, Integer children,
                               Integer withholdingRate, Long mealAllowance, LocalDate hireDate,
                               Boolean seniorCaregiver, Boolean npsEnrolled, Boolean healthEnrolled,
                               Boolean employmentEnrolled) {
    }

    public record ProfileView(Long id, Long memberId, String memberName, String jobType, String payType,
                              int baseAmount, BigDecimal weeklyHours, int dependents, int children,
                              int withholdingRate, int mealAllowance, LocalDate hireDate,
                              boolean seniorCaregiver, boolean npsEnrolled, boolean healthEnrolled,
                              boolean employmentEnrolled, LocalDateTime updatedAt) {
    }

    /** 기타 수당 한 줄 — taxable이 비면 과세로 본다 */
    public record Allowance(String label, Long amount, Boolean taxable) {
    }

    public record Deduction(String label, Long amount) {
    }

    /** 응답에서는 금액이 모두 int다 */
    public record AllowanceView(String label, long amount, boolean taxable) {
    }

    public record DeductionView(String label, long amount) {
    }

    public record LineInput(Long memberId, String payMonth,
                            BigDecimal regularHours, BigDecimal overtimeHours, BigDecimal nightHours,
                            BigDecimal holidayHours, BigDecimal holidayOver8Hours,
                            Long basePay, Long overtimePay, Long nightPay, Long holidayPay,
                            Long longServiceAward, Long seniorAllowance, List<Allowance> otherAllowances,
                            Long mealAllowance, Long grossPay, Long taxablePay,
                            Long nps, Long health, Long ltc, Long employment, Long incomeTax, Long localTax,
                            List<Deduction> otherDeductions, Long netPay,
                            Long employerNps, Long employerHealth, Long employerLtc, Long employerEmployment) {
    }

    public record LineView(Long id, Long memberId, String memberName, String payMonth,
                           BigDecimal regularHours, BigDecimal overtimeHours, BigDecimal nightHours,
                           BigDecimal holidayHours, BigDecimal holidayOver8Hours,
                           int basePay, int overtimePay, int nightPay, int holidayPay,
                           int longServiceAward, int seniorAllowance, List<AllowanceView> otherAllowances,
                           int mealAllowance, int grossPay, int taxablePay,
                           int nps, int health, int ltc, int employment, int incomeTax, int localTax,
                           List<DeductionView> otherDeductions, int netPay,
                           int employerNps, int employerHealth, int employerLtc, int employerEmployment,
                           String status, LocalDateTime confirmedAt, LocalDateTime updatedAt) {
    }

    public record MonthInput(String month) {
    }

    public record ConfirmResult(int confirmed) {
    }

    public record UnconfirmResult(int unconfirmed) {
    }
}
