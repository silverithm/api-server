package com.silverithm.vehicleplacementsystem.dto;

import java.util.List;

/**
 * 오늘 현황 요약.
 *
 * @param vacationNames 오늘 쉬는 사람 이름 — 숫자만 보면 "누가 쉬는지" 확인하려고 근무조정 탭으로
 *                      넘어가야 한다. 대시보드에서 바로 보이도록 함께 내려준다.
 */
public record AttendanceSummaryDTO(
        long total,
        long present,
        long absent,
        long vacation,
        List<String> vacationNames
) {
    /** 이름 목록이 필요 없는 곳(어르신 현황)을 위한 짧은 생성자 */
    public AttendanceSummaryDTO(long total, long present, long absent, long vacation) {
        this(total, present, absent, vacation, List.of());
    }
}
