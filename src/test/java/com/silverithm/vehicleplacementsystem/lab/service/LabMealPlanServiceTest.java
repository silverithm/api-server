package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMealPlanDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMealPlanRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabMealPlanServiceTest {

    @Autowired private LabMealPlanService service;
    @Autowired private LabMealPlanRepository plans;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    // 2026-10-12는 월요일
    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private static LabMealPlanDto.Day day(String main) {
        return new LabMealPlanDto.Day(null,
                new LabMealPlanDto.Lunch("잡곡밥", "된장국", main, "나물", "전", "배추김치"), "과일", "우유", "죽식");
    }

    private static List<LabMealPlanDto.Day> week(String main) {
        List<LabMealPlanDto.Day> days = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            days.add(day(main + i));
        }
        return days;
    }

    private LabMealPlanDto.Input input(LocalDate weekStart, List<LabMealPlanDto.Day> days, String notes) {
        return new LabMealPlanDto.Input(weekStart, days, notes, "영양사", false);
    }

    private void assertBad(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // 같은 기관·같은 주를 다시 저장하면 한 건만 남고 내용이 바뀐다
    @Test
    void upsertByCompanyAndWeekStart() {
        LabMealPlanDto.View first = service.upsert(input(MON, week("제육"), "첫 메모"));
        LabMealPlanDto.View second = service.upsert(
                new LabMealPlanDto.Input(MON, week("생선"), "  고친 메모  ", "새 영양사", true));
        service.upsert(input(MON.plusWeeks(1), week("닭"), null));

        assertThat(plans.count()).isEqualTo(2);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.notes()).isEqualTo("고친 메모");
        assertThat(second.writerName()).isEqualTo("새 영양사");
        assertThat(second.aiAssisted()).isTrue();
        assertThat(second.days()).hasSize(6);
        assertThat(second.days().get(0).lunch().main()).isEqualTo("생선0");
        // 날짜는 서버가 주 시작일 + 순서로 채운다
        assertThat(second.days().get(5).date()).isEqualTo("2026-10-17");

        LabMealPlanDto.View read = service.getByWeek("2026-10-12");
        assertThat(read.days().get(2).lunch().main()).isEqualTo("생선2");
        assertThat(read.days().get(2).soft()).isEqualTo("죽식");

        // 없는 주는 404, 다른 기관은 같은 주여도 따로 저장된다
        assertThatThrownBy(() -> service.getByWeek("2026-11-02")).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThatThrownBy(() -> service.getByWeek("2026-10-12")).isInstanceOf(CustomException.class);
        service.upsert(input(MON, week("국수"), null));
        assertThat(plans.count()).isEqualTo(3);
        assertThat(service.getByWeek("2026-10-12").days().get(0).lunch().main()).isEqualTo("국수0");
    }

    // 주 시작일이 월요일이 아니면 저장도 조회도 400
    @Test
    void weekStartMustBeMonday() {
        for (LocalDate notMonday : new LocalDate[] {MON.plusDays(1), MON.minusDays(1), MON.plusDays(6)}) {
            assertBad(() -> service.upsert(input(notMonday, week("제육"), null)));
            assertBad(() -> service.getByWeek(notMonday.toString()));
        }
        assertBad(() -> service.upsert(input(null, week("제육"), null)));
        assertBad(() -> service.upsert(input(LocalDate.of(1999, 12, 27), week("제육"), null)));
        assertBad(() -> service.getByWeek("내일"));
        assertBad(() -> service.getByWeek(null));
        assertBad(() -> service.upsert(null));
        assertThat(plans.count()).isZero();
        assertThat(service.upsert(input(MON, week("제육"), null)).weekStart()).isEqualTo(MON);
    }

    // days는 정확히 6일, 칸마다 60자 이하, 메모 1000자 이하 — 어기면 400이고 아무것도 저장되지 않는다
    @Test
    void daysJsonLimitsEnforced() {
        List<LabMealPlanDto.Day> five = week("a").subList(0, 5);
        List<LabMealPlanDto.Day> seven = new ArrayList<>(week("a"));
        seven.add(day("a6"));
        assertBad(() -> service.upsert(input(MON, five, null)));
        assertBad(() -> service.upsert(input(MON, seven, null)));
        assertBad(() -> service.upsert(input(MON, null, null)));
        assertBad(() -> service.upsert(input(MON, List.of(), null)));

        List<LabMealPlanDto.Day> nullDay = new ArrayList<>(week("a"));
        nullDay.set(3, null);
        assertBad(() -> service.upsert(input(MON, nullDay, null)));

        // 61자는 거절, 60자는 통과(공백은 정리한 뒤 센다)
        List<LabMealPlanDto.Day> long61 = new ArrayList<>(week("a"));
        long61.set(1, day("가".repeat(61)));
        assertBad(() -> service.upsert(input(MON, long61, null)));
        for (int slot = 0; slot < 8; slot++) {
            int s = slot;
            String big = "나".repeat(61);
            LabMealPlanDto.Lunch l = new LabMealPlanDto.Lunch(s == 0 ? big : "밥", s == 1 ? big : "국",
                    s == 2 ? big : "주", s == 3 ? big : "반", s == 4 ? big : "반", s == 5 ? big : "김");
            List<LabMealPlanDto.Day> d = new ArrayList<>(week("a"));
            d.set(5, new LabMealPlanDto.Day(null, l, s == 6 ? big : "간", s == 7 ? big : "간", s == 7 ? "x" : null));
            assertBad(() -> service.upsert(input(MON, d, null)));
        }
        List<LabMealPlanDto.Day> softLong = new ArrayList<>(week("a"));
        softLong.set(0, new LabMealPlanDto.Day(null, null, null, null, "다".repeat(61)));
        assertBad(() -> service.upsert(input(MON, softLong, null)));
        assertBad(() -> service.upsert(input(MON, week("a"), "라".repeat(1001))));
        // 날짜가 주와 어긋나면 400
        List<LabMealPlanDto.Day> wrongDate = new ArrayList<>(week("a"));
        wrongDate.set(0, new LabMealPlanDto.Day("2026-10-13", null, null, null, null));
        assertBad(() -> service.upsert(input(MON, wrongDate, null)));
        assertBad(() -> service.upsert(new LabMealPlanDto.Input(MON, week("a"), null, "가".repeat(51), false)));
        assertThat(plans.count()).isZero();

        // 경계값은 통과, 빈 칸은 null로 정리된다
        List<LabMealPlanDto.Day> ok = new ArrayList<>(week("a"));
        ok.set(0, new LabMealPlanDto.Day("2026-10-12", null, "  ", "마".repeat(60), null));
        LabMealPlanDto.View saved = service.upsert(input(MON, ok, "바".repeat(1000)));
        assertThat(saved.days().get(0).amSnack()).isNull();
        assertThat(saved.days().get(0).pmSnack()).hasSize(60);
        assertThat(saved.days().get(0).lunch().main()).isNull();
        assertThat(saved.notes()).hasSize(1000);
    }

    // 월 조회는 그 달에 하루라도 걸친 주를 모두 돌려준다 — 앞뒤 달에서 이어지는 주 포함, 한 주 더 먼 주는 제외
    @Test
    void monthFilterReturnsOverlappingWeeks() {
        // 2026-10: 1일(목)~31일(토). 9/28 주는 10/1~10/4가 걸치고, 10/26 주는 10/31이 걸치며, 11/2 주는 안 걸친다
        LocalDate sep21 = LocalDate.of(2026, 9, 21);
        LocalDate sep28 = LocalDate.of(2026, 9, 28);
        LocalDate oct26 = LocalDate.of(2026, 10, 26);
        LocalDate nov2 = LocalDate.of(2026, 11, 2);
        for (LocalDate w : new LocalDate[] {sep21, sep28, MON, oct26, nov2}) {
            service.upsert(input(w, week("제육"), null));
        }
        // 다른 기관 주는 섞이지 않는다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        service.upsert(input(MON.plusWeeks(1), week("남의 것"), null));
        access.companyId = forest.getId();

        assertThat(service.listByMonth("2026-10")).extracting(LabMealPlanDto.View::weekStart)
                .containsExactly(sep28, MON, oct26);
        assertThat(service.listByMonth("2026-09")).extracting(LabMealPlanDto.View::weekStart)
                .containsExactly(sep21, sep28);
        // 11월 1일(일)은 10/26 주의 일요일이 아니라 10/26+6=11/1 — 11월에도 걸친다
        assertThat(service.listByMonth("2026-11")).extracting(LabMealPlanDto.View::weekStart)
                .containsExactly(oct26, nov2);
        assertThat(service.listByMonth("2026-12")).isEmpty();

        assertBad(() -> service.listByMonth("2026-13"));
        assertBad(() -> service.listByMonth("2026-1"));
        assertBad(() -> service.listByMonth(null));
    }

    // 삭제는 자기 기관 것만 — 다른 기관 id는 404이고 남아 있다
    @Test
    void deleteOnlyOwnCompany() {
        Long id = service.upsert(input(MON, week("제육"), null)).id();
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThatThrownBy(() -> service.delete(id)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(plans.count()).isEqualTo(1);
        access.companyId = forest.getId();
        service.delete(id);
        assertThat(plans.count()).isZero();
    }
}
