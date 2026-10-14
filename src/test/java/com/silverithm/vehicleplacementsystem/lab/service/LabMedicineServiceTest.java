package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMedicineDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicineCheckRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabMedicineStockRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabMedicineServiceTest {

    @Autowired private LabMedicineService service;
    @Autowired private LabMedicineStockRepository stock;
    @Autowired private LabMedicineCheckRepository checks;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private LabMedicineDto.StockInput stockInput(String name, LocalDate expires, LocalDate disposed) {
        return new LabMedicineDto.StockInput(name, "소화 불량", 20, "정", "ROOM", expires, null, disposed, null);
    }

    private LabMedicineDto.StockView addStock(String name, LocalDate expires) {
        return service.createStock(stockInput(name, expires, null));
    }

    private LabMedicineDto.CheckInput check(LocalDate day, LabMedicineDto.CheckItem... items) {
        return new LabMedicineDto.CheckInput(day, "박간호사", true, null, List.of(items), null);
    }

    private LabMedicineDto.CheckItem item(Long stockId, String action) {
        return new LabMedicineDto.CheckItem(stockId, true, action, null);
    }

    @Test
    void checkItemsMustBeSameCompanyStock() {
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        LabMedicineDto.StockView foreign = addStock("남의 소화제", LocalDate.of(2026, 6, 1));
        access.companyId = forest.getId();
        LabMedicineDto.StockView mine = addStock("우리 소화제", LocalDate.of(2026, 6, 1));
        LocalDate day = LocalDate.of(2026, 3, 5);

        // 다른 기관 약품 id를 항목에 넣으면 404 — 폐기 표시도, 점검 저장도 없다
        assertRejected(() -> service.upsertCheck(check(day, item(mine.id(), "NONE"), item(foreign.id(), "DISPOSE"))),
                HttpStatus.NOT_FOUND, "약품을 찾을 수 없습니다");
        assertRejected(() -> service.upsertCheck(check(day, item(999_999L, "NONE"))), HttpStatus.NOT_FOUND, null);
        assertThat(checks.count()).isZero();
        assertThat(stock.findById(foreign.id()).orElseThrow().getDisposedOn()).isNull();

        // 같은 약품 두 번·약품 없는 항목·확인 여부 없는 항목·허용 밖 조치는 400
        assertRejected(() -> service.upsertCheck(check(day, item(mine.id(), "NONE"), item(mine.id(), "REORDER"))),
                HttpStatus.BAD_REQUEST, "두 번");
        assertRejected(() -> service.upsertCheck(check(day, item(null, "NONE"))), HttpStatus.BAD_REQUEST, "약품");
        assertRejected(() -> service.upsertCheck(check(day, new LabMedicineDto.CheckItem(mine.id(), null, "NONE",
                null))), HttpStatus.BAD_REQUEST, "이상 없음");
        assertRejected(() -> service.upsertCheck(check(day, item(mine.id(), "BURN"))), HttpStatus.BAD_REQUEST, "조치");
        assertRejected(() -> service.upsertCheck(check(day, new LabMedicineDto.CheckItem(mine.id(), true, "NONE",
                "가".repeat(101)))), HttpStatus.BAD_REQUEST, "100자");
        assertThat(checks.count()).isZero();

        assertThat(service.upsertCheck(check(day, item(mine.id(), "REORDER"))).items()).hasSize(1);
        // 항목은 200개까지
        List<LabMedicineDto.CheckItem> many = new java.util.ArrayList<>();
        for (long i = 0; i < 201; i++) {
            many.add(item(mine.id() + i, "NONE"));
        }
        assertRejected(() -> service.upsertCheck(new LabMedicineDto.CheckInput(day.plusDays(1), "박간호사", true, null,
                many, null)), HttpStatus.BAD_REQUEST, "200개");
    }

    @Test
    void checkDisposeFillsDisposedOn() {
        LocalDate day = LocalDate.of(2026, 3, 5);
        LabMedicineDto.StockView expired = addStock("유통기한 지난 약", LocalDate.of(2026, 1, 1));
        LabMedicineDto.StockView alreadyDisposed = service.createStock(stockInput("이미 버린 약",
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 2, 1)));
        LabMedicineDto.StockView kept = addStock("보관 약", LocalDate.of(2027, 1, 1));

        LabMedicineDto.CheckView saved = service.upsertCheck(check(day, item(expired.id(), "DISPOSE"),
                item(alreadyDisposed.id(), "DISPOSE"), item(kept.id(), "REORDER")));
        em.flush();
        em.clear();

        assertThat(stock.findById(expired.id()).orElseThrow().getDisposedOn()).isEqualTo(day);
        // 이미 폐기일이 있으면 그대로 — 점검일로 덮어쓰지 않는다
        assertThat(stock.findById(alreadyDisposed.id()).orElseThrow().getDisposedOn())
                .isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(stock.findById(kept.id()).orElseThrow().getDisposedOn()).isNull();
        assertThat(saved.items()).extracting(LabMedicineDto.CheckItem::action)
                .containsExactly("DISPOSE", "DISPOSE", "REORDER");
        // 폐기한 약은 기본 목록에서 빠진다
        assertThat(service.listStock(false)).extracting(LabMedicineDto.StockView::name).containsExactly("보관 약");
    }

    @Test
    void checkUpsertByDate() {
        LabMedicineDto.StockView drug = addStock("소화제", LocalDate.of(2026, 6, 1));
        LocalDate day = LocalDate.of(2026, 3, 5);

        LabMedicineDto.CheckView first = service.upsertCheck(new LabMedicineDto.CheckInput(day, "박간호사", false, true,
                List.of(item(drug.id(), "NONE")), "냉장고 열쇠 분실"));
        LabMedicineDto.CheckView again = service.upsertCheck(new LabMedicineDto.CheckInput(day, "최간호", true, false,
                List.of(), null));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(checks.count()).isEqualTo(1);
        assertThat(again.inspectorName()).isEqualTo("최간호");
        assertThat(again.cabinetLocked()).isTrue();
        assertThat(again.fridgeLocked()).isFalse();
        assertThat(again.items()).isEmpty();
        assertThat(again.note()).isNull();
        // fridgeLocked는 냉장 약이 없으면 null로 남는다
        assertThat(service.upsertCheck(new LabMedicineDto.CheckInput(day.plusDays(1), "박간호사", true, null,
                List.of(), null)).fridgeLocked()).isNull();
        assertThat(checks.count()).isEqualTo(2);

        assertRejected(() -> service.upsertCheck(new LabMedicineDto.CheckInput(day, " ", true, null, List.of(), null)),
                HttpStatus.BAD_REQUEST, "점검자 이름");
        assertRejected(() -> service.upsertCheck(new LabMedicineDto.CheckInput(day, "박간호사", null, null, List.of(),
                null)), HttpStatus.BAD_REQUEST, "보관함 잠금");
        assertRejected(() -> service.upsertCheck(new LabMedicineDto.CheckInput(day, "박간호사", true, null, null, null)),
                HttpStatus.BAD_REQUEST, "점검 항목");
        assertRejected(() -> service.upsertCheck(new LabMedicineDto.CheckInput(null, "박간호사", true, null, List.of(),
                null)), HttpStatus.BAD_REQUEST, "점검일");

        // 다른 기관은 같은 날짜로 따로 저장되고, 내 점검은 지울 수 없다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        LabMedicineDto.CheckView theirs = service.upsertCheck(new LabMedicineDto.CheckInput(day, "김간호", true, null,
                List.of(), null));
        assertThat(theirs.id()).isNotEqualTo(first.id());
        assertRejected(() -> service.deleteCheck(first.id()), HttpStatus.NOT_FOUND, null);
        access.companyId = forest.getId();
        service.deleteCheck(first.id());
        assertThat(checks.count()).isEqualTo(2);
    }

    @Test
    void stockExcludesDisposedByDefault() {
        addStock("나중 만료", LocalDate.of(2027, 5, 1));
        addStock("먼저 만료", LocalDate.of(2026, 5, 1));
        LabMedicineDto.StockView disposed = service.createStock(stockInput("폐기됨", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1)));

        assertThat(service.listStock(false)).extracting(LabMedicineDto.StockView::name)
                .containsExactly("먼저 만료", "나중 만료");
        assertThat(service.listStock(true)).extracting(LabMedicineDto.StockView::name)
                .containsExactly("폐기됨", "먼저 만료", "나중 만료");

        // 수정으로 폐기일을 비우면 다시 목록에 나온다
        service.updateStock(disposed.id(), stockInput("폐기됨", LocalDate.of(2026, 1, 1), null));
        assertThat(service.listStock(false)).hasSize(3);

        // 검증 — 수량 범위·보관 방법·필수 칸, 다른 기관 약품은 수정·삭제 불가
        assertRejected(() -> service.createStock(new LabMedicineDto.StockInput("약", "효능", -1, "정", "ROOM",
                LocalDate.of(2026, 5, 1), null, null, null)), HttpStatus.BAD_REQUEST, "수량");
        assertRejected(() -> service.createStock(new LabMedicineDto.StockInput("약", "효능", 100_001, "정", "ROOM",
                LocalDate.of(2026, 5, 1), null, null, null)), HttpStatus.BAD_REQUEST, "수량");
        assertRejected(() -> service.createStock(new LabMedicineDto.StockInput("약", "효능", 1, "정", "SUNNY",
                LocalDate.of(2026, 5, 1), null, null, null)), HttpStatus.BAD_REQUEST, "보관 방법");
        assertRejected(() -> service.createStock(new LabMedicineDto.StockInput("약", "효능", 1, "정", "ROOM", null,
                null, null, null)), HttpStatus.BAD_REQUEST, "사용기한");
        assertRejected(() -> service.createStock(new LabMedicineDto.StockInput(" ", "효능", 1, "정", "ROOM",
                LocalDate.of(2026, 5, 1), null, null, null)), HttpStatus.BAD_REQUEST, "약품 이름");
        assertThat(service.createStock(new LabMedicineDto.StockInput("0개 약", "효능", 0, "정", "FRIDGE",
                LocalDate.of(2026, 5, 1), null, null, null)).quantity()).isZero();
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listStock(true)).isEmpty();
        assertRejected(() -> service.deleteStock(disposed.id()), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.updateStock(disposed.id(), stockInput("x", LocalDate.of(2026, 5, 1), null)),
                HttpStatus.NOT_FOUND, null);
    }

    @Test
    void yearFilter() {
        service.upsertCheck(check(LocalDate.of(2025, 12, 31)));
        service.upsertCheck(check(LocalDate.of(2026, 12, 31)));
        service.upsertCheck(check(LocalDate.of(2026, 1, 1)));
        service.upsertCheck(check(LocalDate.of(2026, 7, 15)));
        service.upsertCheck(check(LocalDate.of(2027, 1, 1)));

        assertThat(service.listChecks("2026")).extracting(LabMedicineDto.CheckView::checkedOn)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 12, 31));
        assertThat(service.listChecks("2025")).hasSize(1);
        assertThat(service.listChecks("2024")).isEmpty();
        assertRejected(() -> service.listChecks("26"), HttpStatus.BAD_REQUEST, "YYYY");
        assertRejected(() -> service.listChecks(null), HttpStatus.BAD_REQUEST, "YYYY");
    }
}
