package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBusinessPlanDto.Item;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBusinessPlanRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabBusinessPlanServiceTest {

    @Autowired private LabBusinessPlanService service;
    @Autowired private LabBusinessPlanRepository plans;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private Item item(String name, Long budget) {
        return new Item(name, "목표", "내용", "전체 어르신", budget, "연중");
    }

    private LabBusinessPlanDto.Input input(Integer year, List<Item> operation, List<Item> service, List<Item> staff) {
        return new LabBusinessPlanDto.Input(year, new LabBusinessPlanDto.Sections(operation, service, staff),
                50_000_000L, 48_000_000L, LocalDate.of(2026, 1, 10), "박사회", false);
    }

    @Test
    void upsertByYear() {
        assertRejected(() -> service.getByYear("2026"), HttpStatus.NOT_FOUND, "사업계획서가 없습니다");
        assertRejected(() -> service.getByYear("26"), HttpStatus.BAD_REQUEST, "연도");

        LabBusinessPlanDto.View first = service.upsert(input(2026, List.of(item("기관 운영 개선", 1_000_000L)),
                List.of(item("프로그램 운영", 2_000_000L), item("식단 개선", 0L)), List.of(item("직원 교육", null))));
        assertThat(first.sections().operation()).hasSize(1);
        assertThat(first.sections().service()).extracting(Item::name).containsExactly("프로그램 운영", "식단 개선");
        assertThat(first.sections().service().get(1).budget()).isZero();
        assertThat(first.sections().staff().get(0).budget()).isNull();
        assertThat(first.totalIncome()).isEqualTo(50_000_000L);
        assertThat(first.totalExpense()).isEqualTo(48_000_000L);
        assertThat(first.writtenOn()).isEqualTo(LocalDate.of(2026, 1, 10));

        // JSON 필드 이름이 계약 모양 그대로 저장·복원된다
        LabBusinessPlanDto.View read = service.getByYear("2026");
        Item op = read.sections().operation().get(0);
        assertThat(op.goal()).isEqualTo("목표");
        assertThat(op.content()).isEqualTo("내용");
        assertThat(op.target()).isEqualTo("전체 어르신");
        assertThat(op.schedule()).isEqualTo("연중");

        LabBusinessPlanDto.View again = service.upsert(input(2026, List.of(), List.of(), List.of(item("새 계획", 5L))));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(plans.count()).isEqualTo(1);
        assertThat(service.getByYear("2026").sections().operation()).isEmpty();
        assertThat(service.getByYear("2026").sections().staff()).extracting(Item::name).containsExactly("새 계획");

        service.upsert(input(2027, null, null, null));
        assertThat(plans.count()).isEqualTo(2);
        assertThat(service.getByYear("2027").sections().service()).isEmpty();

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.getByYear("2026"), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.delete(first.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.delete(first.id());
        assertRejected(() -> service.getByYear("2026"), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void sectionsValidated() {
        assertRejected(() -> service.upsert(new LabBusinessPlanDto.Input(2026, null, null, null, null, null, null)),
                HttpStatus.BAD_REQUEST, "분야별");
        assertRejected(() -> service.upsert(input(null, List.of(), List.of(), List.of())), HttpStatus.BAD_REQUEST,
                "계획 연도");
        assertRejected(() -> service.upsert(input(2026, List.of(item("가".repeat(61), 1L)), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "사업명");
        assertRejected(() -> service.upsert(input(2026, List.of(), List.of(new Item("이름", "가".repeat(301), "내용",
                "대상", 1L, "일정")), List.of())), HttpStatus.BAD_REQUEST, "목표");
        assertRejected(() -> service.upsert(input(2026, List.of(), List.of(), List.of(new Item("이름", "목표",
                "가".repeat(1001), "대상", 1L, "일정")))), HttpStatus.BAD_REQUEST, "내용");
        assertRejected(() -> service.upsert(input(2026, List.of(new Item("이름", "목표", "내용", "가".repeat(101), 1L,
                "일정")), List.of(), List.of())), HttpStatus.BAD_REQUEST, "대상");
        assertRejected(() -> service.upsert(input(2026, List.of(new Item("이름", "목표", "내용", "대상", 1L,
                "가".repeat(101))), List.of(), List.of())), HttpStatus.BAD_REQUEST, "일정");
        assertRejected(() -> service.upsert(input(2026, java.util.Arrays.asList(item("a", 1L), null), List.of(),
                List.of())), HttpStatus.BAD_REQUEST, "비어 있습니다");
        assertRejected(() -> service.upsert(new LabBusinessPlanDto.Input(2026, new LabBusinessPlanDto.Sections(
                List.of(), List.of(), List.of()), 10_000_000_001L, null, null, null, null)), HttpStatus.BAD_REQUEST,
                "총수입");
        assertRejected(() -> service.upsert(new LabBusinessPlanDto.Input(2026, new LabBusinessPlanDto.Sections(
                List.of(), List.of(), List.of()), null, -1L, null, null, null)), HttpStatus.BAD_REQUEST, "총지출");

        // 분야마다 20개 — 21개째는 막고, 20개는 세 분야 모두 허용
        List<Item> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            twenty.add(item("항목" + i, 1L));
        }
        List<Item> twentyOne = new ArrayList<>(twenty);
        twentyOne.add(item("넘침", 1L));
        assertRejected(() -> service.upsert(input(2026, twentyOne, List.of(), List.of())), HttpStatus.BAD_REQUEST,
                "기관운영 항목은 최대 20개");
        assertRejected(() -> service.upsert(input(2026, List.of(), twentyOne, List.of())), HttpStatus.BAD_REQUEST,
                "서비스 제공 항목은 최대 20개");
        assertRejected(() -> service.upsert(input(2026, List.of(), List.of(), twentyOne)), HttpStatus.BAD_REQUEST,
                "직원관리 항목은 최대 20개");
        assertThat(plans.count()).isZero();
        LabBusinessPlanDto.View ok = service.upsert(input(2026, twenty, twenty, twenty));
        assertThat(ok.sections().operation()).hasSize(20);
        assertThat(ok.sections().service()).hasSize(20);
        assertThat(ok.sections().staff()).hasSize(20);

        // 작성 중 저장: 칸을 비워 둔 항목은 null로 저장된다
        LabBusinessPlanDto.View draft = service.upsert(input(2026, List.of(new Item(" ", null, "", null, null, null)),
                List.of(), List.of()));
        Item blank = draft.sections().operation().get(0);
        assertThat(blank.name()).isNull();
        assertThat(blank.budget()).isNull();
    }

    @Test
    void negativeBudgetRejected() {
        assertRejected(() -> service.upsert(input(2026, List.of(item("기관", -1L)), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "예산");
        assertRejected(() -> service.upsert(input(2026, List.of(), List.of(item("서비스", 100L), item("서비스2", -500L)),
                List.of())), HttpStatus.BAD_REQUEST, "서비스 제공 2번째 항목의 예산");
        assertRejected(() -> service.upsert(input(2026, List.of(), List.of(), List.of(item("직원", 10_000_000_001L)))),
                HttpStatus.BAD_REQUEST, "예산");
        assertThat(plans.count()).isZero();
        // 0원은 허용
        assertThat(service.upsert(input(2026, List.of(item("기관", 0L)), List.of(), List.of())).sections().operation()
                .get(0).budget()).isZero();
    }
}
