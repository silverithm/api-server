package com.silverithm.vehicleplacementsystem.lab.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 월 인력배치 요청·응답. 입력은 id·updatedAt을 뺀 것이다. */
public final class LabStaffingDto {

    private LabStaffingDto() {
    }

    /** 직원 1명의 그 달 근무 — memberId는 기관 직원과 이은 경우만 있다 */
    public record Staff(Long memberId, String name, String jobType, BigDecimal hours) {
    }

    public record Input(String staffingMonth, Long serviceDays, Long usersSum, String extraUsersNote,
                        List<String> holidays, List<Staff> staff, String note, String writerName) {
    }

    public record View(Long id, String staffingMonth, int serviceDays, int usersSum, String extraUsersNote,
                       List<String> holidays, List<Staff> staff, String note, String writerName,
                       LocalDateTime updatedAt) {
    }
}
