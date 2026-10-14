package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabDonationDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabLedgerDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabLedgerEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabLedgerServiceTest {

    @Autowired private LabLedgerService service;
    @Autowired private LabDonationService donationService;
    @Autowired private LabLedgerEntryRepository entries;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private LabLedgerDto.Input input(LocalDate date, String direction, String code, long amount, Long donationId) {
        return new LabLedgerDto.Input(date, direction, code, "식재료 구입", amount, "행복마트", "CARD", donationId,
                false);
    }

    private LabLedgerDto.Input input(LocalDate date, String code) {
        return input(date, "EXPENSE", code, 10_000L, null);
    }

    private LabDonationDto.View donation(String receiptNo) {
        return donationService.create(new LabDonationDto.Input(LocalDate.of(2026, 3, 2), "김후원", "INDIVIDUAL",
                "CASH", 500_000L, null, null, receiptNo));
    }

    // 계정 코드는 3자리 숫자이고, 금액은 1~100억 사이다
    @Test
    void accountCodeValidated() {
        LocalDate day = LocalDate.of(2026, 3, 10);
        for (String bad : new String[] {"12", "1234", "abc", "1a3", "", " ", "１２３"}) {
            assertStatus(() -> service.create(input(day, bad)), HttpStatus.BAD_REQUEST);
        }
        assertStatus(() -> service.create(input(day, null)), HttpStatus.BAD_REQUEST);
        assertThat(entries.count()).isZero();

        assertThat(service.create(input(day, "000")).accountCode()).isEqualTo("000");
        assertThat(service.create(input(day, "421")).accountCode()).isEqualTo("421");
        assertThat(service.create(input(day, " 421 ")).accountCode()).isEqualTo("421");

        // 수정에서도 같은 검증
        Long id = service.create(input(day, "311")).id();
        assertStatus(() -> service.update(id, input(day, "31")), HttpStatus.BAD_REQUEST);
        assertThat(service.update(id, input(day, "312")).accountCode()).isEqualTo("312");

        // 금액 범위 — 0·음수·100억 초과는 400, 100억은 통과(BIGINT)
        assertStatus(() -> service.create(input(day, "EXPENSE", "311", 0L, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(day, "EXPENSE", "311", -5L, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(day, "EXPENSE", "311", 10_000_000_001L, null)),
                HttpStatus.BAD_REQUEST);
        assertThat(service.create(input(day, "INCOME", "100", 10_000_000_000L, null)).amount())
                .isEqualTo(10_000_000_000L);

        // 구분·증빙·적요·날짜
        assertStatus(() -> service.create(input(day, "OUT", "311", 1L, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabLedgerDto.Input(day, "EXPENSE", "311", "적요", 1L, null, "CASH", null,
                false)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabLedgerDto.Input(day, "EXPENSE", "311", "  ", 1L, null, null, null,
                false)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabLedgerDto.Input(day, "EXPENSE", "311", "가".repeat(201), 1L, null,
                null, null, false)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabLedgerDto.Input(null, "EXPENSE", "311", "적요", 1L, null, null, null,
                false)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabLedgerDto.Input(LocalDate.of(1999, 1, 1), "EXPENSE", "311", "적요",
                1L, null, null, null, false)), HttpStatus.BAD_REQUEST);
    }

    // 후원금 연결은 같은 기관 후원금만 — 다른 기관·없는 후원금은 404
    @Test
    void donationLinkMustBeSameCompany() {
        LabDonationDto.View mine = donation("R-1");
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        LabDonationDto.View theirs = donation("R-1");
        access.companyId = forest.getId();

        LocalDate day = LocalDate.of(2026, 3, 10);
        assertStatus(() -> service.create(input(day, "EXPENSE", "311", 1_000L, theirs.id())), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.create(input(day, "EXPENSE", "311", 1_000L, 999_999L)), HttpStatus.NOT_FOUND);
        assertThat(entries.count()).isZero();

        LabLedgerDto.View linked = service.create(input(day, "EXPENSE", "311", 1_000L, mine.id()));
        assertThat(linked.donationId()).isEqualTo(mine.id());
        // 수정으로 다른 기관 후원금에 옮겨 붙일 수도 없다
        assertStatus(() -> service.update(linked.id(), input(day, "EXPENSE", "311", 1_000L, theirs.id())),
                HttpStatus.NOT_FOUND);
        assertThat(entries.findById(linked.id()).orElseThrow().getDonationId()).isEqualTo(mine.id());
        // 연결을 풀 수는 있다
        assertThat(service.update(linked.id(), input(day, "EXPENSE", "311", 1_000L, null)).donationId()).isNull();

        // 다른 기관의 장부 기록은 수정·삭제할 수 없다
        access.companyId = other.getId();
        assertStatus(() -> service.update(linked.id(), input(day, "311")), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(linked.id()), HttpStatus.NOT_FOUND);
        access.companyId = forest.getId();
        service.delete(linked.id());
        assertThat(entries.count()).isZero();
    }

    // 월·연도 조회는 둘 중 하나만, 날짜·id 순, 월 경계를 지킨다
    @Test
    void monthAndYearFilters() {
        Long c = service.create(input(LocalDate.of(2026, 3, 31), "311")).id();
        Long a = service.create(input(LocalDate.of(2026, 3, 1), "311")).id();
        Long b = service.create(input(LocalDate.of(2026, 3, 1), "312")).id();
        service.create(input(LocalDate.of(2026, 4, 1), "311"));
        service.create(input(LocalDate.of(2026, 2, 28), "311"));
        service.create(input(LocalDate.of(2025, 12, 31), "311"));
        service.create(input(LocalDate.of(2027, 1, 1), "311"));

        // 같은 날은 id 순, 날짜가 이르면 id가 커도 앞
        assertThat(service.list("2026-03", null)).extracting(LabLedgerDto.View::id).containsExactly(a, b, c);
        assertThat(service.list(null, "2026")).hasSize(5);
        assertThat(service.list(null, "2026")).extracting(LabLedgerDto.View::entryDate).isSorted();
        assertThat(service.list("2024-03", null)).isEmpty();
        // 윤년 2월 말일
        service.create(input(LocalDate.of(2028, 2, 29), "311"));
        assertThat(service.list("2028-02", null)).hasSize(1);

        assertStatus(() -> service.list(null, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.list("2026-03", "2026"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.list("2026-3", null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.list(null, "26"), HttpStatus.BAD_REQUEST);

        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertThat(service.list(null, "2026")).isEmpty();
    }
}
