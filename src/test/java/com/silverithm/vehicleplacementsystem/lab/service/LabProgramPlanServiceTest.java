package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto.ScheduleItem;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramFeedbackRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramPlanRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabProgramPlanServiceTest {

    private static final LocalDate WRITTEN = LocalDate.of(2026, 1, 5);

    @Autowired private LabProgramPlanService service;
    @Autowired private LabProgramPlanRepository plans;
    @Autowired private LabProgramScheduleRepository schedules;
    @Autowired private LabProgramFeedbackRepository feedbacks;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private LabProgramPlanDto.PlanInput plan(Integer year, String category, String goal) {
        return new LabProgramPlanDto.PlanInput(year, category, goal, "회상 놀이와 색칠 활동", "주 3회", "전체 어르신",
                1_200_000L, WRITTEN, "박사회", false);
    }

    private ScheduleItem item(String date, String category, String title) {
        return new ScheduleItem(date, "10:30", category, title, "김강사");
    }

    private LabProgramPlanDto.ScheduleInput schedule(String month, List<ScheduleItem> items, LocalDate sharedOn,
                                                     String method) {
        return new LabProgramPlanDto.ScheduleInput(month, items, "메모", sharedOn, method, false);
    }

    private LabProgramPlanDto.FeedbackInput feedback(String half, LocalDate collectedOn, String reflection,
                                                     LocalDate reflectedOn) {
        return new LabProgramPlanDto.FeedbackInput(half, collectedOn, "SURVEY", 12, "노래 교실을 더 원하십니다",
                reflection, reflectedOn, "박사회", false);
    }

    @Test
    void upsertByYearAndCategory() {
        LabProgramPlanDto.PlanView first = service.upsertPlan(plan(2026, "COGNITIVE", "인지 유지"));
        LabProgramPlanDto.PlanView again = service.upsertPlan(plan(2026, "COGNITIVE", "인지 유지와 정서 안정"));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.goal()).isEqualTo("인지 유지와 정서 안정");
        assertThat(plans.count()).isEqualTo(1);

        service.upsertPlan(plan(2026, "PHYSICAL", "근력 유지"));
        service.upsertPlan(plan(2025, "COGNITIVE", "작년 계획"));
        assertThat(service.listPlans("2026")).extracting(LabProgramPlanDto.PlanView::category)
                .containsExactly("COGNITIVE", "PHYSICAL");
        assertThat(service.listPlans("2025")).hasSize(1);
        assertThat(service.listPlans("2030")).isEmpty();
        assertThat(again.budget()).isEqualTo(1_200_000L);
        assertThat(again.frequency()).isEqualTo("주 3회");

        // 다른 기관은 같은 연도·분야여도 별개이고, 남의 계획은 지울 수 없다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listPlans("2026")).isEmpty();
        assertRejected(() -> service.deletePlan(first.id()), HttpStatus.NOT_FOUND, null);
        service.upsertPlan(plan(2026, "COGNITIVE", "다른 기관 계획"));
        assertThat(plans.count()).isEqualTo(4);
        access.companyId = forest.getId();
        service.deletePlan(first.id());
        assertThat(plans.count()).isEqualTo(3);
    }

    @Test
    void categoryValidated() {
        assertRejected(() -> service.upsertPlan(plan(2026, "LEISURE", "목표")), HttpStatus.BAD_REQUEST, "분야");
        assertRejected(() -> service.upsertPlan(plan(2026, null, "목표")), HttpStatus.BAD_REQUEST, "분야");
        assertRejected(() -> service.upsertPlan(plan(2026, "SOCIAL", " ")), HttpStatus.BAD_REQUEST, "목표");
        assertRejected(() -> service.upsertPlan(plan(2026, "SOCIAL", "가".repeat(501))), HttpStatus.BAD_REQUEST,
                "500자");
        assertRejected(() -> service.upsertPlan(plan(null, "SOCIAL", "목표")), HttpStatus.BAD_REQUEST, "계획 연도");
        assertRejected(() -> service.upsertPlan(plan(1999, "SOCIAL", "목표")), HttpStatus.BAD_REQUEST, "계획 연도");
        assertRejected(() -> service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표", " ", "월 1회",
                null, null, WRITTEN, null, null)), HttpStatus.BAD_REQUEST, "세부 프로그램");
        assertRejected(() -> service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표", "내용", null,
                null, null, WRITTEN, null, null)), HttpStatus.BAD_REQUEST, "실시 횟수");
        assertRejected(() -> service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표", "내용", "월 1회",
                null, null, null, null, null)), HttpStatus.BAD_REQUEST, "작성일");
        assertRejected(() -> service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표", "내용", "월 1회",
                null, -1L, WRITTEN, null, null)), HttpStatus.BAD_REQUEST, "예산");
        assertRejected(() -> service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표", "내용", "월 1회",
                null, 1_000_000_001L, WRITTEN, null, null)), HttpStatus.BAD_REQUEST, "예산");
        assertRejected(() -> service.listPlans("26"), HttpStatus.BAD_REQUEST, "연도");
        assertThat(plans.count()).isZero();

        // 예산·대상은 비워도 저장되고, 세 분야는 모두 허용된다
        LabProgramPlanDto.PlanView ok = service.upsertPlan(new LabProgramPlanDto.PlanInput(2026, "SOCIAL", "목표",
                "내용", "월 1회", " ", null, WRITTEN, null, null));
        assertThat(ok.budget()).isNull();
        assertThat(ok.target()).isNull();
        service.upsertPlan(plan(2026, "PHYSICAL", "목표"));
        service.upsertPlan(plan(2026, "COGNITIVE", "목표"));
        assertThat(plans.count()).isEqualTo(3);
    }

    @Test
    void scheduleItemsOutsideMonthRejected() {
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-04-01", "PHYSICAL", "체조")),
                null, null)), HttpStatus.BAD_REQUEST, "밖");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-02-28", "PHYSICAL", "체조")),
                null, null)), HttpStatus.BAD_REQUEST, "밖");
        // 같은 달이라도 연도가 다르면 밖이다
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2025-03-10", "PHYSICAL", "체조")),
                null, null)), HttpStatus.BAD_REQUEST, "밖");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-03-31T10", "PHYSICAL", "체조")),
                null, null)), HttpStatus.BAD_REQUEST, "날짜");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-03-10", "YOGA", "체조")), null,
                null)), HttpStatus.BAD_REQUEST, "분야");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-03-10", "PHYSICAL", " ")), null,
                null)), HttpStatus.BAD_REQUEST, "제목");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(item("2026-03-10", "PHYSICAL",
                "가".repeat(41))), null, null)), HttpStatus.BAD_REQUEST, "40자");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", List.of(new ScheduleItem("2026-03-10", "25:00",
                "PHYSICAL", "체조", null)), null, null)), HttpStatus.BAD_REQUEST, "시작 시각");
        assertRejected(() -> service.upsertSchedule(schedule("2026-13", List.of(), null, null)), HttpStatus.BAD_REQUEST,
                "YYYY-MM");
        List<ScheduleItem> tooMany = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            tooMany.add(item("2026-03-10", "OTHER", "항목" + i));
        }
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", tooMany, null, null)), HttpStatus.BAD_REQUEST,
                "200개");
        assertThat(schedules.count()).isZero();

        // 정확히 200개는 되고, 달의 첫날·마지막날도 그 달 안이다
        List<ScheduleItem> exactly = new ArrayList<>(tooMany.subList(0, 198));
        exactly.add(item("2026-03-01", "SOCIAL", "첫날"));
        exactly.add(item("2026-03-31", "LEISURE", "마지막날"));
        assertThat(service.upsertSchedule(schedule("2026-03", exactly, null, null)).items()).hasSize(200);
    }

    @Test
    void scheduleSharedPairValidated() {
        List<ScheduleItem> items = List.of(item("2026-03-10", "PHYSICAL", "체조"));
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", items, LocalDate.of(2026, 2, 27), null)),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", items, null, "PRINT")), HttpStatus.BAD_REQUEST,
                "함께");
        assertRejected(() -> service.upsertSchedule(schedule("2026-03", items, LocalDate.of(2026, 2, 27), "FAX")),
                HttpStatus.BAD_REQUEST, "제공 방법");
        assertThat(schedules.count()).isZero();

        LabProgramPlanDto.ScheduleView shared = service.upsertSchedule(schedule("2026-03", items,
                LocalDate.of(2026, 2, 27), "KAKAO"));
        assertThat(shared.sharedOn()).isEqualTo(LocalDate.of(2026, 2, 27));
        assertThat(shared.sharedMethod()).isEqualTo("KAKAO");
        // 둘 다 비우면 제공 전 상태로 저장된다
        LabProgramPlanDto.ScheduleView unshared = service.upsertSchedule(schedule("2026-03", items, null, " "));
        assertThat(unshared.sharedOn()).isNull();
        assertThat(unshared.sharedMethod()).isNull();
        assertThat(unshared.id()).isEqualTo(shared.id());
    }

    @Test
    void scheduleUpsertByMonth() {
        // 없으면 404
        assertRejected(() -> service.getSchedule("2026-03"), HttpStatus.NOT_FOUND, "계획표가 없습니다");
        assertRejected(() -> service.getSchedule("2026-3"), HttpStatus.BAD_REQUEST, "YYYY-MM");

        LabProgramPlanDto.ScheduleView first = service.upsertSchedule(schedule("2026-03", List.of(
                item("2026-03-12", "COGNITIVE", "퍼즐"), new ScheduleItem("2026-03-02", null, "PHYSICAL", "체조", null),
                item("2026-03-02", "SOCIAL", "노래")), null, null));
        // 날짜·시각 순으로 정렬되고 JSON 항목이 그대로 돌아온다
        assertThat(first.items()).extracting(ScheduleItem::title).containsExactly("체조", "노래", "퍼즐");
        assertThat(first.items().get(1).instructor()).isEqualTo("김강사");
        assertThat(first.items().get(0).startTime()).isNull();

        LabProgramPlanDto.ScheduleView again = service.upsertSchedule(schedule("2026-03",
                List.of(item("2026-03-20", "OTHER", "생신잔치")), null, null));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(schedules.count()).isEqualTo(1);
        assertThat(service.getSchedule("2026-03").items()).extracting(ScheduleItem::title).containsExactly("생신잔치");

        service.upsertSchedule(schedule("2026-04", List.of(), null, null));
        service.upsertSchedule(schedule("2025-12", List.of(), null, null));
        assertThat(service.listSchedules("2026")).extracting(LabProgramPlanDto.ScheduleView::scheduleMonth)
                .containsExactly("2026-03", "2026-04");

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.getSchedule("2026-03"), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.deleteSchedule(first.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.deleteSchedule(first.id());
        assertRejected(() -> service.getSchedule("2026-03"), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void feedbackCollectedOutsideHalfRejected() {
        assertRejected(() -> service.createFeedback(feedback("2026-H1", LocalDate.of(2026, 7, 1), null, null)),
                HttpStatus.BAD_REQUEST, "2026-H1 기간");
        assertRejected(() -> service.createFeedback(feedback("2026-H1", LocalDate.of(2025, 12, 31), null, null)),
                HttpStatus.BAD_REQUEST, "기간");
        assertRejected(() -> service.createFeedback(feedback("2026-H2", LocalDate.of(2026, 6, 30), null, null)),
                HttpStatus.BAD_REQUEST, "기간");
        assertRejected(() -> service.createFeedback(feedback("2026-H3", LocalDate.of(2026, 6, 30), null, null)),
                HttpStatus.BAD_REQUEST, "YYYY-H1");
        assertRejected(() -> service.createFeedback(feedback("2026-1", LocalDate.of(2026, 6, 30), null, null)),
                HttpStatus.BAD_REQUEST, "YYYY-H1");
        assertRejected(() -> service.createFeedback(feedback(null, LocalDate.of(2026, 6, 30), null, null)),
                HttpStatus.BAD_REQUEST, "YYYY-H1");
        assertRejected(() -> service.createFeedback(feedback("2026-H1", null, null, null)), HttpStatus.BAD_REQUEST,
                "수집일");
        assertThat(feedbacks.count()).isZero();

        // 경계일은 안이고, 반기당 여러 건을 쌓을 수 있다
        service.createFeedback(feedback("2026-H1", LocalDate.of(2026, 1, 1), null, null));
        service.createFeedback(feedback("2026-H1", LocalDate.of(2026, 6, 30), null, null));
        service.createFeedback(feedback("2026-H2", LocalDate.of(2026, 7, 1), null, null));
        service.createFeedback(feedback("2026-H2", LocalDate.of(2026, 12, 31), null, null));
        service.createFeedback(feedback("2025-H2", LocalDate.of(2025, 12, 31), null, null));
        assertThat(service.listFeedback("2026")).extracting(LabProgramPlanDto.FeedbackView::collectedOn)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31));

        // 수정으로 반기 밖 날짜로 옮기는 것도 막는다
        LabProgramPlanDto.FeedbackView saved = service.listFeedback("2026").get(0);
        assertRejected(() -> service.updateFeedback(saved.id(), feedback("2026-H1", LocalDate.of(2026, 8, 1), null,
                null)), HttpStatus.BAD_REQUEST, "기간");
        assertRejected(() -> service.updateFeedback(99999L, feedback("2026-H1", LocalDate.of(2026, 2, 1), null, null)),
                HttpStatus.NOT_FOUND, "의견수렴 기록");
        LabProgramPlanDto.FeedbackView moved = service.updateFeedback(saved.id(), feedback("2026-H2",
                LocalDate.of(2026, 8, 1), null, null));
        assertThat(moved.half()).isEqualTo("2026-H2");
        assertThat(feedbacks.count()).isEqualTo(5);
        // 참여 인원 범위, 요약 필수
        assertRejected(() -> service.createFeedback(new LabProgramPlanDto.FeedbackInput("2026-H1",
                LocalDate.of(2026, 3, 1), "MEETING", 501, "요약", null, null, null, null)), HttpStatus.BAD_REQUEST,
                "참여 인원");
        assertRejected(() -> service.createFeedback(new LabProgramPlanDto.FeedbackInput("2026-H1",
                LocalDate.of(2026, 3, 1), "MEETING", 3, " ", null, null, null, null)), HttpStatus.BAD_REQUEST,
                "의견 요약");
        assertRejected(() -> service.createFeedback(new LabProgramPlanDto.FeedbackInput("2026-H1",
                LocalDate.of(2026, 3, 1), "PHONE", 3, "요약", null, null, null, null)), HttpStatus.BAD_REQUEST,
                "수렴 방법");
    }

    @Test
    void feedbackReflectionPairValidated() {
        LocalDate collected = LocalDate.of(2026, 3, 10);
        assertRejected(() -> service.createFeedback(feedback("2026-H1", collected, "노래 교실을 주 1회 늘렸다", null)),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.createFeedback(feedback("2026-H1", collected, null, LocalDate.of(2026, 4, 1))),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.createFeedback(feedback("2026-H1", collected, " ", LocalDate.of(2026, 4, 1))),
                HttpStatus.BAD_REQUEST, "함께");
        assertRejected(() -> service.createFeedback(feedback("2026-H1", collected, "반영", collected.minusDays(1))),
                HttpStatus.BAD_REQUEST, "수집일보다 앞");
        assertThat(feedbacks.count()).isZero();

        // 같은 날 반영은 허용(수집일 이상)
        LabProgramPlanDto.FeedbackView sameDay = service.createFeedback(feedback("2026-H1", collected, "반영", collected));
        assertThat(sameDay.reflectedOn()).isEqualTo(collected);
        // 반영 없이 수집만 한 기록은 허용되고, 수정으로 반영을 붙일 수 있다
        LabProgramPlanDto.FeedbackView pending = service.createFeedback(feedback("2026-H1", collected, null, null));
        assertThat(pending.reflection()).isNull();
        assertThat(pending.reflectedOn()).isNull();
        LabProgramPlanDto.FeedbackView reflected = service.updateFeedback(pending.id(), feedback("2026-H1", collected,
                "점심 후 노래 시간을 만들었다", LocalDate.of(2026, 9, 1)));
        assertThat(reflected.reflection()).isEqualTo("점심 후 노래 시간을 만들었다");
        assertThat(reflected.reflectedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        // 반영을 다시 비우면 날짜도 같이 비워야 한다
        assertRejected(() -> service.updateFeedback(pending.id(), feedback("2026-H1", collected, null,
                LocalDate.of(2026, 9, 1))), HttpStatus.BAD_REQUEST, "함께");
    }

    @Test
    void feedbackSummaryEncryptedAtRest() {
        LabProgramPlanDto.FeedbackView saved = service.createFeedback(feedback("2026-H1", LocalDate.of(2026, 3, 10),
                "점심 후 노래 시간을 만들었다", LocalDate.of(2026, 4, 1)));
        em.flush();
        em.clear();

        assertEncryptedAtRest(jdbc, "lab_program_feedback", "summary", "노래 교실");
        assertEncryptedAtRest(jdbc, "lab_program_feedback", "reflection", "노래 시간");
        LabProgramPlanDto.FeedbackView read = service.listFeedback("2026").get(0);
        assertThat(read.id()).isEqualTo(saved.id());
        assertThat(read.summary()).isEqualTo("노래 교실을 더 원하십니다");
        assertThat(read.reflection()).isEqualTo("점심 후 노래 시간을 만들었다");
        assertThat(read.participants()).isEqualTo(12);
        assertThat(read.method()).isEqualTo("SURVEY");

        // 다른 기관은 보지도 지우지도 못한다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listFeedback("2026")).isEmpty();
        assertRejected(() -> service.deleteFeedback(saved.id()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.updateFeedback(saved.id(), feedback("2026-H1", LocalDate.of(2026, 3, 10), null,
                null)), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.deleteFeedback(saved.id());
        assertThat(feedbacks.count()).isZero();
    }
}
