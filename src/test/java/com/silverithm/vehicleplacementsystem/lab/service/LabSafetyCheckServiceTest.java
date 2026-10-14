package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabSafetyCheckDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabSafetyCheckRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabSafetyCheckServiceTest {

    @Autowired private LabSafetyCheckService service;
    @Autowired private LabSafetyCheckRepository checks;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private static LabSafetyCheckDto.Item item(String code, Boolean ok, String note) {
        return new LabSafetyCheckDto.Item(code, ok, note);
    }

    private LabSafetyCheckDto.Input input(String kind, LocalDate date, List<LabSafetyCheckDto.Item> items) {
        return new LabSafetyCheckDto.Input(date, kind, items, null, "시설장", false);
    }

    private LabSafetyCheckDto.View save(String kind, LocalDate date) {
        return service.upsert(input(kind, date, List.of(item("FIRE_EXTINGUISHER", true, null))));
    }

    private void assertBad(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // 같은 기관·종류·날짜는 한 건 — 다시 저장하면 고쳐지고, 종류나 날짜가 다르면 따로 남는다
    @Test
    void upsertByKindAndDate() {
        LabSafetyCheckDto.View first = service.upsert(new LabSafetyCheckDto.Input(DAY, "KITCHEN",
                List.of(item("FRIDGE_TEMP", false, "  5도 넘음  "), item("FLOOR_DRY", null, null)), null, "조리사",
                false));
        LabSafetyCheckDto.View second = service.upsert(new LabSafetyCheckDto.Input(DAY, "KITCHEN",
                List.of(item("FRIDGE_TEMP", true, "조치 후 정상")), "냉장고 수리 요청", "영양사", true));
        save("HYGIENE", DAY);
        save("KITCHEN", DAY.plusDays(1));

        assertThat(checks.count()).isEqualTo(3);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.items().get(0).note()).isEqualTo("5도 넘음");
        assertThat(first.items().get(1).ok()).isNull();
        assertThat(second.items()).hasSize(1);
        assertThat(second.items().get(0).ok()).isTrue();
        assertThat(second.actions()).isEqualTo("냉장고 수리 요청");
        assertThat(second.inspectorName()).isEqualTo("영양사");
        assertThat(second.aiAssisted()).isTrue();

        // 다른 기관은 같은 종류·날짜여도 따로 저장된다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        save("KITCHEN", DAY);
        assertThat(checks.count()).isEqualTo(4);
        assertThat(service.listByMonth("2026-10", "KITCHEN")).hasSize(1);
    }

    // kind는 일곱 가지만, 항목은 60개 이하·code 형식·중복·메모 200자·조치 2000자 — 어기면 400, 저장 없음
    @Test
    void itemCodeValidated() {
        for (String kind : new String[] {"kitchen", "OTHER", "", null}) {
            assertBad(() -> service.upsert(input(kind, DAY, List.of(item("AB", true, null)))));
        }
        for (String code : new String[] {"A", "a1", "AB-C", "AB C", "한글코드", "A".repeat(31), "", " ", null}) {
            assertBad(() -> service.upsert(input("FIRE", DAY, List.of(item(code, true, null)))));
        }
        assertBad(() -> service.upsert(input("FIRE", DAY, null)));
        assertBad(() -> service.upsert(input("FIRE", null, List.of())));
        assertBad(() -> service.upsert(input("FIRE", LocalDate.of(2101, 1, 1), List.of())));
        assertBad(() -> service.upsert(null));
        List<LabSafetyCheckDto.Item> withNull = new ArrayList<>();
        withNull.add(null);
        assertBad(() -> service.upsert(input("FIRE", DAY, withNull)));
        assertBad(() -> service.upsert(input("FIRE", DAY, List.of(item("DUP_ONE", true, null), item("DUP_ONE", false, null)))));
        assertBad(() -> service.upsert(input("FIRE", DAY, List.of(item("AB", true, "가".repeat(201))))));
        assertBad(() -> service.upsert(new LabSafetyCheckDto.Input(DAY, "FIRE", List.of(), "나".repeat(2001), null, null)));
        assertBad(() -> service.upsert(new LabSafetyCheckDto.Input(DAY, "FIRE", List.of(), null, "다".repeat(51), null)));

        List<LabSafetyCheckDto.Item> sixtyOne = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            sixtyOne.add(item("ITEM_" + i, true, null));
        }
        assertBad(() -> service.upsert(input("FIRE", DAY, sixtyOne)));
        assertThat(checks.count()).isZero();

        // 경계: 60개, code 2자·30자, 메모 200자, 조치 2000자는 통과
        List<LabSafetyCheckDto.Item> sixty = new ArrayList<>(sixtyOne.subList(0, 58));
        sixty.add(item("AB", false, "라".repeat(200)));
        sixty.add(item("A".repeat(30), null, null));
        LabSafetyCheckDto.View saved = service.upsert(new LabSafetyCheckDto.Input(DAY, " FIRE ", sixty,
                "마".repeat(2000), null, null));
        assertThat(saved.items()).hasSize(60);
        assertThat(saved.kind()).isEqualTo("FIRE");
        assertThat(saved.actions()).hasSize(2000);
        assertThat(checks.count()).isEqualTo(1);
        for (String kind : new String[] {"KITCHEN", "HYGIENE", "FACILITY", "VEHICLE", "INFECTION", "DRILL"}) {
            save(kind, DAY);
        }
        assertThat(checks.count()).isEqualTo(7);
    }

    // 월·연도 조회 — 경계 날짜 포함, kind 필터, 연도는 재난훈련 연 2회 확인용
    @Test
    void yearFilterForDrill() {
        save("DRILL", LocalDate.of(2026, 1, 1));
        save("DRILL", LocalDate.of(2026, 6, 30));
        save("DRILL", LocalDate.of(2026, 12, 31));
        save("DRILL", LocalDate.of(2025, 12, 31));
        save("DRILL", LocalDate.of(2027, 1, 1));
        save("FIRE", LocalDate.of(2026, 6, 1));

        assertThat(service.listByYear("2026", "DRILL")).extracting(LabSafetyCheckDto.View::checkDate)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 12, 31));
        assertThat(service.listByYear("2026", null)).hasSize(4);
        assertThat(service.listByYear("2026", "FIRE")).hasSize(1);

        // 월 조회: 말일·1일 경계와 kind 선택
        save("KITCHEN", LocalDate.of(2026, 6, 15));
        assertThat(service.listByMonth("2026-06", null)).extracting(LabSafetyCheckDto.View::kind)
                .containsExactly("FIRE", "KITCHEN", "DRILL");
        assertThat(service.listByMonth("2026-06", "DRILL")).hasSize(1);
        assertThat(service.listByMonth("2026-01", "DRILL")).hasSize(1);
        assertThat(service.listByMonth("2026-12", "DRILL")).hasSize(1);
        assertThat(service.listByMonth("2026-02", null)).isEmpty();

        assertBad(() -> service.listByYear("20x6", "DRILL"));
        assertBad(() -> service.listByYear("99999", "DRILL"));
        assertBad(() -> service.listByYear(null, "DRILL"));
        assertBad(() -> service.listByYear("2026", "NOPE"));
        assertBad(() -> service.listByMonth("2026-00", null));
        assertBad(() -> service.listByMonth("2026-06", "NOPE"));
    }

    // 다른 기관 기록은 조회·삭제 모두 안 보이고 건드릴 수 없다
    @Test
    void otherCompanyCannotSee() {
        Long id = save("DRILL", DAY).id();
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();

        assertThat(service.listByMonth("2026-10", null)).isEmpty();
        assertThat(service.listByYear("2026", "DRILL")).isEmpty();
        assertThatThrownBy(() -> service.delete(id)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(checks.count()).isEqualTo(1);

        // 같은 키로 저장해도 남의 행을 고치지 않고 자기 행을 새로 만든다
        service.upsert(new LabSafetyCheckDto.Input(DAY, "DRILL", List.of(item("EXIT_ROUTE", false, null)),
                "다른기관 조치", null, null));
        assertThat(checks.count()).isEqualTo(2);

        access.companyId = forest.getId();
        LabSafetyCheckDto.View mine = service.listByMonth("2026-10", "DRILL").get(0);
        assertThat(mine.id()).isEqualTo(id);
        assertThat(mine.actions()).isNull();
        service.delete(id);
        assertThat(checks.count()).isEqualTo(1);
    }
}
