package com.silverithm.vehicleplacementsystem.lab.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 직원 인사 기준·이수/검진 기록 요청·응답. memberName은 서버가 채우므로 입력에는 없다. */
public final class LabStaffHrDto {

    private LabStaffHrDto() {
    }

    public record ProfileInput(Long memberId, LocalDate hireDate, String jobType, Integer birthYear,
                               LocalDate licensePassedOn, Boolean officeWorker, String note) {
    }

    public record ProfileView(Long id, Long memberId, String memberName, LocalDate hireDate, String jobType,
                              Integer birthYear, LocalDate licensePassedOn, boolean officeWorker, String note,
                              LocalDateTime updatedAt) {
    }

    public record CertInput(Long memberId, String kind, LocalDate doneOn, BigDecimal hours, String institution,
                            String certNo, String note) {
    }

    public record CertView(Long id, Long memberId, String memberName, String kind, LocalDate doneOn,
                           BigDecimal hours, String institution, String certNo, String note,
                           LocalDateTime updatedAt) {
    }
}
