package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.VacationDeadlineDate;
import com.silverithm.vehicleplacementsystem.repository.VacationDeadlineDateRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴무 입력 마감일의 월별 직접 지정.
 *
 * 기관마다 "셋째 주 일요일"처럼 달마다 날짜가 달라지는 마감일을 쓰기 때문에,
 * 매월 고정일(VacationDeadlineSetting.deadlineDay)만으로는 표현할 수 없다.
 * 특정 달에 지정한 날짜가 있으면 그 날짜가 고정일보다 우선한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacationDeadlineDateService {

    private final VacationDeadlineDateRepository repository;

    /** 기관이 지정한 월별 마감일 전체 — {"2026-08": "2026-08-16", ...} */
    @Transactional(readOnly = true)
    public Map<String, String> getDeadlineDates(Long companyId) {
        Map<String, String> result = new HashMap<>();
        for (VacationDeadlineDate item : repository.findByCompanyIdOrderByTargetMonthAsc(companyId)) {
            result.put(item.getTargetMonth(), item.getDeadlineDate().toString());
        }
        return result;
    }

    /**
     * 특정 달의 마감일을 지정하거나(날짜 있음) 해제한다(날짜 null).
     * 지정한 날짜가 속한 달을 target_month로 삼으므로 호출자가 달을 따로 넘기지 않는다.
     */
    @Transactional
    public void saveDeadlineDate(Long companyId, String targetMonth, LocalDate deadlineDate) {
        if (targetMonth == null || targetMonth.isBlank()) {
            throw new IllegalArgumentException("대상 월이 필요합니다");
        }
        // yyyy-MM 형식 검증 — 잘못된 값이 들어오면 조회 시 조용히 누락되므로 여기서 막는다
        YearMonth month = YearMonth.parse(targetMonth);

        if (deadlineDate == null) {
            repository.deleteByCompanyIdAndTargetMonth(companyId, targetMonth);
            return;
        }
        if (!YearMonth.from(deadlineDate).equals(month)) {
            throw new IllegalArgumentException("마감일은 대상 월 안의 날짜여야 합니다");
        }

        VacationDeadlineDate entity = repository.findByCompanyIdAndTargetMonth(companyId, targetMonth)
                .orElseGet(() -> VacationDeadlineDate.builder()
                        .companyId(companyId)
                        .targetMonth(targetMonth)
                        .deadlineDate(deadlineDate)
                        .build());
        entity.setDeadlineDate(deadlineDate);
        repository.save(entity);
    }

    /**
     * 그 달에 적용되는 마감일. 월별 지정이 있으면 그 날짜, 없으면 고정일로 계산한다.
     * 고정일이 말일을 넘으면 말일로 클램프한다.
     */
    @Transactional(readOnly = true)
    public LocalDate resolveDeadline(Long companyId, YearMonth month, Integer fallbackDay) {
        return repository.findByCompanyIdAndTargetMonth(companyId, month.toString())
                .map(VacationDeadlineDate::getDeadlineDate)
                .orElseGet(() -> fallbackDay == null
                        ? null
                        : month.atDay(Math.min(fallbackDay, month.lengthOfMonth())));
    }

    /** 지난 달들의 지정은 쌓이기만 하므로 조회 편의를 위해 최근 것만 남긴다 (오래된 것 정리) */
    @Transactional
    public void pruneBefore(Long companyId, YearMonth keepFrom) {
        List<VacationDeadlineDate> all = repository.findByCompanyIdOrderByTargetMonthAsc(companyId);
        List<VacationDeadlineDate> stale = all.stream()
                .filter(item -> YearMonth.parse(item.getTargetMonth()).isBefore(keepFrom))
                .toList();
        if (!stale.isEmpty()) {
            repository.deleteAll(stale);
        }
    }
}
