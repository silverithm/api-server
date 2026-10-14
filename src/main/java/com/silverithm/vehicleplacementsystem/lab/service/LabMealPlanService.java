package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMealPlanDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabMealPlan;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMealPlanRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주간 식단표 — 기관 x 주(월요일 시작) 1건. 월~토 6일, 칸마다 60자 이하.
 *
 * <p>days는 JSON 문자열 컬럼에 담기므로 받은 값을 그대로 저장하지 않고, 검증을 거친 값만 다시 직렬화한다
 * (알 수 없는 필드나 긴 글이 몰래 저장되지 않게).
 */
@Service
public class LabMealPlanService {

    static final int DAYS = 6;
    private static final int SLOT = 60;
    private static final int NOTES = 1000;
    private static final int NAME = 50;

    private final LabAccessService access;
    private final LabMealPlanRepository plans;
    private final TransactionTemplate tx;

    public LabMealPlanService(LabAccessService access, LabMealPlanRepository plans,
                              PlatformTransactionManager txManager) {
        this.access = access;
        this.plans = plans;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 그 주 식단 — 없으면 404. 날짜가 월요일이 아니면 400 */
    @Transactional(readOnly = true)
    public LabMealPlanDto.View getByWeek(String weekStart) {
        Long companyId = access.requireCompanyId();
        LocalDate week = monday(LabValidation.date(weekStart, "주 시작일"));
        return plans.findByCompanyIdAndWeekStart(companyId, week).map(LabMealPlanService::toView)
                .orElseThrow(() -> new CustomException("해당 주 식단이 없습니다", HttpStatus.NOT_FOUND));
    }

    /**
     * 그 달에 하루라도 걸친 주 모두. 주는 월~일 7일로 보므로 월요일이 (달 1일 - 6일) 이상, 달 말일 이하인 주다.
     * (예: 9월 30일 화요일이 속한 주는 10월 조회에도 나온다)
     */
    @Transactional(readOnly = true)
    public List<LabMealPlanDto.View> listByMonth(String month) {
        Long companyId = access.requireCompanyId();
        YearMonth ym = LabValidation.yearMonth(month, "월");
        return plans.findByCompanyIdAndWeekStartBetweenOrderByWeekStartAsc(companyId,
                ym.atDay(1).minusDays(6), ym.atEndOfMonth()).stream().map(LabMealPlanService::toView).toList();
    }

    public LabMealPlanDto.View upsert(LabMealPlanDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        if (input == null) {
            throw LabValidation.bad("식단 내용을 보내 주세요");
        }
        LocalDate week = monday(LabValidation.plausibleDate(input.weekStart(), "주 시작일"));
        String days = LabJson.write(days(input.days(), week));
        String notes = LabValidation.text(input.notes(), NOTES, "식단 메모");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabMealPlan saved = LabValidation.upsertWithRetry(tx, () -> {
            LabMealPlan plan = plans.findByCompanyIdAndWeekStart(caller.companyId(), week)
                    .orElseGet(() -> new LabMealPlan(caller.companyId(), week));
            plan.fill(days, notes, writer, ai);
            return plans.saveAndFlush(plan);
        });
        return toView(saved);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        plans.delete(plans.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("식단을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static LocalDate monday(LocalDate date) {
        if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw LabValidation.bad("주 시작일은 월요일이어야 합니다");
        }
        return date;
    }

    /** 6일이 아니면 400. 칸마다 앞뒤 공백을 정리하고 60자를 넘으면 400. date는 비었거나 week + 순서와 같아야 한다 */
    private static List<LabMealPlanDto.Day> days(List<LabMealPlanDto.Day> input, LocalDate week) {
        if (input == null || input.size() != DAYS) {
            throw LabValidation.bad("식단은 월~토 " + DAYS + "일치를 보내 주세요");
        }
        List<LabMealPlanDto.Day> result = new ArrayList<>(DAYS);
        for (int i = 0; i < DAYS; i++) {
            LabMealPlanDto.Day day = input.get(i);
            if (day == null) {
                throw LabValidation.bad((i + 1) + "일째 식단이 비어 있습니다");
            }
            String expected = week.plusDays(i).toString();
            String date = LabValidation.text(day.date(), 10, "날짜");
            if (date != null && !date.equals(expected)) {
                throw LabValidation.bad((i + 1) + "일째 날짜가 주 시작일과 맞지 않습니다");
            }
            LabMealPlanDto.Lunch l = day.lunch();
            LabMealPlanDto.Lunch lunch = l == null ? new LabMealPlanDto.Lunch(null, null, null, null, null, null)
                    : new LabMealPlanDto.Lunch(slot(l.rice(), "밥"), slot(l.soup(), "국"), slot(l.main(), "주찬"),
                            slot(l.side1(), "반찬1"), slot(l.side2(), "반찬2"), slot(l.kimchi(), "김치"));
            result.add(new LabMealPlanDto.Day(expected, lunch, slot(day.amSnack(), "오전 간식"),
                    slot(day.pmSnack(), "오후 간식"), slot(day.soft(), "대체식 메모")));
        }
        return result;
    }

    private static String slot(String value, String label) {
        return LabValidation.text(value, SLOT, label);
    }

    private static LabMealPlanDto.View toView(LabMealPlan p) {
        return new LabMealPlanDto.View(p.getId(), p.getWeekStart(), LabJson.readMealDays(p.getDays()), p.getNotes(),
                p.getWriterName(), p.isAiAssisted(), p.getUpdatedAt());
    }
}
