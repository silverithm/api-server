package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramFeedback;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramPlan;
import com.silverithm.vehicleplacementsystem.lab.entity.LabProgramSchedule;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramFeedbackRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramPlanRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramScheduleRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 프로그램 연간계획(평가 24 신체·25 인지·26 사회적응), 월간 프로그램 계획표(평가 17 보호자 제공 증빙),
 * 반기별 의견수렴·반영.
 *
 * <p>연간계획은 기관 x 연도 x 분야, 계획표는 기관 x 달로 한 건이라 PUT이 upsert다. 계획표 항목은 JSON 문자열
 * 컬럼에 담기므로 받은 값을 그대로 저장하지 않고 검증·정리한 값만 다시 직렬화한다.
 * 의견 요약·반영 내용은 암호화한다.
 */
@Service
public class LabProgramPlanService {

    static final Set<String> PLAN_CATEGORIES = Set.of("PHYSICAL", "COGNITIVE", "SOCIAL");
    static final Set<String> ITEM_CATEGORIES = Set.of("COGNITIVE", "PHYSICAL", "LEISURE", "SOCIAL", "OTHER");
    static final Set<String> SHARED_METHODS = Set.of("PRINT", "SMS", "KAKAO", "BOARD", "OTHER");
    static final Set<String> FEEDBACK_METHODS = Set.of("SURVEY", "INTERVIEW", "MEETING", "OTHER");
    static final int MAX_ITEMS = 200;
    private static final long MAX_BUDGET = 1_000_000_000L;
    private static final int NAME = 50;
    private static final Pattern HALF = Pattern.compile("^(\\d{4})-H([12])$");

    private final LabAccessService access;
    private final LabProgramPlanRepository plans;
    private final LabProgramScheduleRepository schedules;
    private final LabProgramFeedbackRepository feedbacks;
    private final TransactionTemplate tx;

    public LabProgramPlanService(LabAccessService access, LabProgramPlanRepository plans,
                                 LabProgramScheduleRepository schedules, LabProgramFeedbackRepository feedbacks,
                                 PlatformTransactionManager txManager) {
        this.access = access;
        this.plans = plans;
        this.schedules = schedules;
        this.feedbacks = feedbacks;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 연간계획 ----

    @Transactional(readOnly = true)
    public List<LabProgramPlanDto.PlanView> listPlans(String year) {
        Long companyId = access.requireCompanyId();
        short y = (short) LabValidation.year(year, "연도");
        return plans.findByCompanyIdAndPlanYearOrderByCategoryAsc(companyId, y).stream()
                .map(LabProgramPlanService::toView).toList();
    }

    public LabProgramPlanDto.PlanView upsertPlan(LabProgramPlanDto.PlanInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("연간계획 내용을 보내 주세요");
        }
        short year = (short) LabValidation.year(input.planYear(), "계획 연도");
        String category = LabValidation.oneOf(input.category(), PLAN_CATEGORIES, "분야");
        String goal = LabValidation.requiredText(input.goal(), 500, "목표");
        String content = LabValidation.requiredText(input.content(), 3000, "세부 프로그램·방법");
        String frequency = LabValidation.requiredText(input.frequency(), 40, "실시 횟수");
        String target = LabValidation.text(input.target(), 100, "대상");
        Long budget = input.budget() == null ? null : LabValidation.longInRange(input.budget(), 0, MAX_BUDGET, "예산");
        LocalDate writtenOn = LabValidation.plausibleDate(input.writtenOn(), "작성일");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabProgramPlan saved = LabValidation.upsertWithRetry(tx, () -> {
            LabProgramPlan plan = plans.findByCompanyIdAndPlanYearAndCategory(companyId, year, category)
                    .orElseGet(() -> new LabProgramPlan(companyId, year, category));
            plan.fill(goal, content, frequency, target, budget, writtenOn, writer, ai);
            return plans.saveAndFlush(plan);
        });
        return toView(saved);
    }

    @Transactional
    public void deletePlan(Long id) {
        Long companyId = access.requireCompanyId();
        plans.delete(plans.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("연간계획을 찾을 수 없습니다")));
    }

    // ---- 월간 계획표 ----

    /** 그 달 계획표 — 없으면 404 */
    @Transactional(readOnly = true)
    public LabProgramPlanDto.ScheduleView getSchedule(String month) {
        Long companyId = access.requireCompanyId();
        String m = LabValidation.month(month, "월");
        return schedules.findByCompanyIdAndScheduleMonth(companyId, m).map(LabProgramPlanService::toView)
                .orElseThrow(() -> notFound("해당 월 계획표가 없습니다"));
    }

    /** 그 해 계획표 전부(달 오름차순) */
    @Transactional(readOnly = true)
    public List<LabProgramPlanDto.ScheduleView> listSchedules(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return schedules.findByCompanyIdAndScheduleMonthStartingWithOrderByScheduleMonthAsc(companyId, y + "-")
                .stream().map(LabProgramPlanService::toView).toList();
    }

    public LabProgramPlanDto.ScheduleView upsertSchedule(LabProgramPlanDto.ScheduleInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("계획표 내용을 보내 주세요");
        }
        String month = LabValidation.month(input.scheduleMonth(), "계획표 월");
        LabValidation.plausibleDate(YearMonth.parse(month).atDay(1), "계획표 월");
        String items = LabJson.write(items(input.items(), YearMonth.parse(month)));
        String notes = LabValidation.text(input.notes(), 500, "메모");
        LocalDate sharedOn = input.sharedOn() == null ? null
                : LabValidation.plausibleDate(input.sharedOn(), "보호자 제공일");
        String sharedMethod = LabValidation.text(input.sharedMethod(), 12, "제공 방법");
        if ((sharedOn == null) != (sharedMethod == null)) {
            throw LabValidation.bad("보호자 제공일과 제공 방법은 함께 적어야 합니다");
        }
        if (sharedMethod != null) {
            LabValidation.oneOf(sharedMethod, SHARED_METHODS, "제공 방법");
        }
        boolean ai = Boolean.TRUE.equals(input.aiAssisted());

        LabProgramSchedule saved = LabValidation.upsertWithRetry(tx, () -> {
            LabProgramSchedule schedule = schedules.findByCompanyIdAndScheduleMonth(companyId, month)
                    .orElseGet(() -> new LabProgramSchedule(companyId, month));
            schedule.fill(items, notes, sharedOn, sharedMethod, ai);
            return schedules.saveAndFlush(schedule);
        });
        return toView(saved);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        Long companyId = access.requireCompanyId();
        schedules.delete(schedules.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("계획표를 찾을 수 없습니다")));
    }

    /** 200개 이하. 날짜는 그 달 안, 분야는 허용 값, 제목은 필수 40자. 날짜·시각 순으로 정렬해 저장한다 */
    private static List<LabProgramPlanDto.ScheduleItem> items(List<LabProgramPlanDto.ScheduleItem> input,
                                                              YearMonth month) {
        if (input == null) {
            return List.of();
        }
        if (input.size() > MAX_ITEMS) {
            throw LabValidation.bad("계획표 항목은 최대 " + MAX_ITEMS + "개까지 쓸 수 있습니다");
        }
        List<LabProgramPlanDto.ScheduleItem> result = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            LabProgramPlanDto.ScheduleItem item = input.get(i);
            String where = (i + 1) + "번째 항목의 ";
            if (item == null) {
                throw LabValidation.bad((i + 1) + "번째 항목이 비어 있습니다");
            }
            LocalDate date = LabValidation.date(item.date(), where + "날짜");
            if (!YearMonth.from(date).equals(month)) {
                throw LabValidation.bad(where + "날짜가 계획표 월(" + month + ") 밖입니다");
            }
            String startTime = item.startTime() == null || item.startTime().isBlank() ? null
                    : LabValidation.clock(item.startTime(), where + "시작 시각");
            String category = LabValidation.oneOf(item.category(), ITEM_CATEGORIES, where + "분야");
            String title = LabValidation.requiredText(item.title(), 40, where + "제목");
            String instructor = LabValidation.text(item.instructor(), 30, where + "강사");
            result.add(new LabProgramPlanDto.ScheduleItem(date.toString(), startTime, category, title, instructor));
        }
        result.sort(Comparator.comparing(LabProgramPlanDto.ScheduleItem::date)
                .thenComparing(it -> it.startTime() == null ? "" : it.startTime()));
        return result;
    }

    // ---- 의견수렴·반영 ----

    @Transactional(readOnly = true)
    public List<LabProgramPlanDto.FeedbackView> listFeedback(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        return feedbacks.findByCompanyIdAndHalfInOrderByCollectedOnAscIdAsc(companyId,
                List.of(y + "-H1", y + "-H2")).stream().map(LabProgramPlanService::toView).toList();
    }

    @Transactional
    public LabProgramPlanDto.FeedbackView createFeedback(LabProgramPlanDto.FeedbackInput input) {
        Long companyId = access.requireCompanyId();
        LabProgramFeedback feedback = new LabProgramFeedback(companyId);
        apply(feedback, input);
        return toView(feedbacks.saveAndFlush(feedback));
    }

    @Transactional
    public LabProgramPlanDto.FeedbackView updateFeedback(Long id, LabProgramPlanDto.FeedbackInput input) {
        Long companyId = access.requireCompanyId();
        LabProgramFeedback feedback = feedbacks.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("의견수렴 기록을 찾을 수 없습니다"));
        apply(feedback, input);
        return toView(feedbacks.saveAndFlush(feedback));
    }

    @Transactional
    public void deleteFeedback(Long id) {
        Long companyId = access.requireCompanyId();
        feedbacks.delete(feedbacks.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("의견수렴 기록을 찾을 수 없습니다")));
    }

    private void apply(LabProgramFeedback feedback, LabProgramPlanDto.FeedbackInput input) {
        if (input == null) {
            throw LabValidation.bad("의견수렴 내용을 보내 주세요");
        }
        String half = input.half() == null ? null : input.half().strip();
        Matcher m = half == null ? null : HALF.matcher(half);
        if (m == null || !m.matches()) {
            throw LabValidation.bad("반기는 YYYY-H1 또는 YYYY-H2 형식이어야 합니다");
        }
        int year = LabValidation.year(Integer.parseInt(m.group(1)), "반기 연도");
        boolean first = "1".equals(m.group(2));
        LocalDate from = first ? LocalDate.of(year, 1, 1) : LocalDate.of(year, 7, 1);
        LocalDate to = first ? LocalDate.of(year, 6, 30) : LocalDate.of(year, 12, 31);
        LocalDate collectedOn = LabValidation.plausibleDate(input.collectedOn(), "수집일");
        if (collectedOn.isBefore(from) || collectedOn.isAfter(to)) {
            throw LabValidation.bad("수집일이 " + half + " 기간(" + from + " ~ " + to + ") 안이어야 합니다");
        }
        String method = LabValidation.oneOf(input.method(), FEEDBACK_METHODS, "수렴 방법");
        short participants = (short) LabValidation.longInRange(
                input.participants() == null ? null : input.participants().longValue(), 0, 500, "참여 인원");
        String summary = LabValidation.requiredText(input.summary(), 2000, "의견 요약");
        String reflection = LabValidation.text(input.reflection(), 2000, "반영 내용");
        LocalDate reflectedOn = input.reflectedOn() == null ? null
                : LabValidation.plausibleDate(input.reflectedOn(), "반영일");
        if ((reflection == null) != (reflectedOn == null)) {
            throw LabValidation.bad("반영 내용과 반영일은 함께 적어야 합니다");
        }
        if (reflectedOn != null && reflectedOn.isBefore(collectedOn)) {
            throw LabValidation.bad("반영일이 수집일보다 앞일 수 없습니다");
        }
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        feedback.fill(half, collectedOn, method, participants, summary, reflection, reflectedOn, writer,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static CustomException notFound(String message) {
        return new CustomException(message, HttpStatus.NOT_FOUND);
    }

    private static LabProgramPlanDto.PlanView toView(LabProgramPlan p) {
        return new LabProgramPlanDto.PlanView(p.getId(), p.getPlanYear(), p.getCategory(), p.getGoal(),
                p.getContent(), p.getFrequency(), p.getTarget(), p.getBudget(), p.getWrittenOn(), p.getWriterName(),
                p.isAiAssisted(), p.getUpdatedAt());
    }

    private static LabProgramPlanDto.ScheduleView toView(LabProgramSchedule s) {
        return new LabProgramPlanDto.ScheduleView(s.getId(), s.getScheduleMonth(),
                LabJson.readScheduleItems(s.getItems()), s.getNotes(), s.getSharedOn(), s.getSharedMethod(),
                s.isAiAssisted(), s.getUpdatedAt());
    }

    private static LabProgramPlanDto.FeedbackView toView(LabProgramFeedback f) {
        return new LabProgramPlanDto.FeedbackView(f.getId(), f.getHalf(), f.getCollectedOn(), f.getMethod(),
                f.getParticipants(), f.getSummary(), f.getReflection(), f.getReflectedOn(), f.getWriterName(),
                f.isAiAssisted(), f.getUpdatedAt());
    }
}
