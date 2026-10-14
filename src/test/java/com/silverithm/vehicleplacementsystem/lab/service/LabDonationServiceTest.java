package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabDonationDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabLedgerDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabDonationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabDonationServiceTest {

    @Autowired private LabDonationService service;
    @Autowired private LabLedgerService ledgerService;
    @Autowired private LabDonationRepository donations;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 2);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    private LabDonationDto.Input input(String donor, long amount, String receiptNo) {
        return new LabDonationDto.Input(DAY, donor, "INDIVIDUAL", "CASH", amount, null, null, receiptNo);
    }

    private void spend(long amount, String direction, Long donationId) {
        ledgerService.create(new LabLedgerDto.Input(LocalDate.of(2026, 4, 1), direction, "311", "물품 구입", amount,
                null, null, donationId, false));
    }

    // 후원자 이름은 DB에 암호문으로 저장되고 응답에서는 평문으로 돌아온다
    @Test
    void donorNameEncryptedAtRest() {
        LabDonationDto.View saved = service.create(input("김후원", 100_000L, "R-1"));
        assertThat(saved.donorName()).isEqualTo("김후원");
        em.flush();
        em.clear();
        String raw = jdbc.queryForObject("SELECT donor_name FROM lab_donations WHERE id = ?", String.class,
                saved.id());
        assertThat(raw).startsWith(EncryptedPiiConverter.ENC_PREFIX).doesNotContain("김후원");
        assertThat(service.listByYear("2026").get(0).donorName()).isEqualTo("김후원");

        // 이름 길이·필수, 금액·종류 검증
        assertStatus(() -> service.create(input("가".repeat(51), 1L, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(" ", 1L, null)), HttpStatus.BAD_REQUEST);
        assertThat(service.create(input("가".repeat(50), 1L, null)).donorName()).hasSize(50);
        assertStatus(() -> service.create(input("김후원", 0L, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input("김후원", 10_000_000_001L, null)), HttpStatus.BAD_REQUEST);
        assertThat(service.create(input("김후원", 10_000_000_000L, null)).amount()).isEqualTo(10_000_000_000L);
        assertStatus(() -> service.create(new LabDonationDto.Input(DAY, "김후원", "ALIEN", "CASH", 1L, null, null,
                null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabDonationDto.Input(DAY, "김후원", "GROUP", "STOCK", 1L, null, null,
                null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabDonationDto.Input(DAY, "김후원", "GROUP", "GOODS", 1L,
                "가".repeat(201), null, null)), HttpStatus.BAD_REQUEST);
        // 현금이면 물품 설명을 저장하지 않고, 물품이면 남는다. 지정 용도는 비면 NULL
        assertThat(service.create(new LabDonationDto.Input(DAY, "김후원", "GROUP", "CASH", 1L, "쌀", "  ", null))
                .goodsDescription()).isNull();
        LabDonationDto.View goods = service.create(new LabDonationDto.Input(DAY, "김후원", "CORPORATE", "GOODS",
                50_000L, "쌀 10kg", "급식비", null));
        assertThat(goods.goodsDescription()).isEqualTo("쌀 10kg");
        assertThat(goods.designatedPurpose()).isEqualTo("급식비");
    }

    // usedAmount는 연결된 지출(EXPENSE) 합 — 수입 연결·미연결·다른 기관 지출은 세지 않는다
    @Test
    void usedAmountSumsLinkedExpenses() {
        LabDonationDto.View a = service.create(input("김후원", 500_000L, "A-1"));
        LabDonationDto.View b = service.create(input("이후원", 300_000L, "A-2"));
        LabDonationDto.View c = service.create(input("박후원", 200_000L, null));
        assertThat(service.listByYear("2026")).extracting(LabDonationDto.View::usedAmount).containsExactly(0L, 0L, 0L);

        spend(100_000L, "EXPENSE", a.id());
        spend(30_000L, "EXPENSE", a.id());
        spend(7_000L, "EXPENSE", b.id());
        spend(999_999L, "EXPENSE", null);
        spend(5_000L, "INCOME", a.id());
        // 100억 지출 두 건도 BIGINT로 합쳐진다
        spend(10_000_000_000L, "EXPENSE", c.id());
        spend(10_000_000_000L, "EXPENSE", c.id());

        List<LabDonationDto.View> list = service.listByYear("2026");
        assertThat(list).extracting(LabDonationDto.View::usedAmount)
                .containsExactly(130_000L, 7_000L, 20_000_000_000L);

        // 수정 응답에도 사용액이 있다
        assertThat(service.update(a.id(), input("김후원", 500_000L, "A-1")).usedAmount()).isEqualTo(130_000L);

        // 다른 기관의 같은 번호 후원금과 지출은 섞이지 않는다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listByYear("2026")).isEmpty();
        LabDonationDto.View theirs = service.create(input("김후원", 1L, "A-1"));
        assertThat(theirs.usedAmount()).isZero();
        access.companyId = forest.getId();
        assertThat(service.listByYear("2026")).hasSize(3);

        // 연도 경계 — 연도 밖 후원금은 목록에 없다
        service.create(new LabDonationDto.Input(LocalDate.of(2025, 12, 31), "연말", "GROUP", "CASH", 1L, null, null,
                null));
        service.create(new LabDonationDto.Input(LocalDate.of(2027, 1, 1), "연초", "GROUP", "CASH", 1L, null, null,
                null));
        assertThat(service.listByYear("2026")).hasSize(3);
        assertStatus(() -> service.listByYear("26"), HttpStatus.BAD_REQUEST);
    }

    // 연결된 지출이 있으면 삭제 409, 연결을 풀면 삭제된다. 수입만 연결돼 있으면 삭제되고 연결은 풀린다
    @Test
    void deleteWithLinkedExpenseRejected() {
        LabDonationDto.View a = service.create(input("김후원", 500_000L, "A-1"));
        spend(10_000L, "EXPENSE", a.id());
        assertStatus(() -> service.delete(a.id()), HttpStatus.CONFLICT);
        assertThat(donations.count()).isEqualTo(1);

        Long expenseId = ledgerService.list("2026-04", null).get(0).id();
        ledgerService.update(expenseId, new LabLedgerDto.Input(LocalDate.of(2026, 4, 1), "EXPENSE", "311", "물품 구입",
                10_000L, null, null, null, false));
        service.delete(a.id());
        assertThat(donations.count()).isZero();

        // 다른 기관은 지울 수 없고(404), 없는 id도 404
        LabDonationDto.View b = service.create(input("이후원", 1L, "B-1"));
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertStatus(() -> service.delete(b.id()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.update(b.id(), input("이후원", 1L, "B-1")), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.delete(999_999L), HttpStatus.NOT_FOUND);
        assertThat(donations.count()).isEqualTo(1);
    }

    // 영수증 번호는 기관 안에서 유일(대소문자 무시) — 다른 기관은 같은 번호를 쓸 수 있고 NULL은 여러 건
    @Test
    void receiptNoUniquePerCompany() {
        LabDonationDto.View a = service.create(input("김후원", 1L, "2026-001"));
        assertStatus(() -> service.create(input("이후원", 1L, "2026-001")), HttpStatus.CONFLICT);
        assertStatus(() -> service.create(input("이후원", 1L, " 2026-001 ")), HttpStatus.CONFLICT);
        assertStatus(() -> service.create(input("이후원", 1L, "2026-001".toLowerCase())), HttpStatus.CONFLICT);
        assertThat(donations.count()).isEqualTo(1);

        // 번호가 없으면(NULL·공백) 여러 건 가능
        service.create(input("무번호1", 1L, null));
        service.create(input("무번호2", 1L, "  "));
        assertThat(donations.count()).isEqualTo(3);

        // 다른 번호로 접수하고, 자기 번호로 다시 저장하는 것은 되지만 남의 번호로 바꾸면 409
        LabDonationDto.View b = service.create(input("이후원", 1L, "2026-002"));
        assertThat(service.update(b.id(), input("이후원", 2L, "2026-002")).amount()).isEqualTo(2L);
        assertStatus(() -> service.update(b.id(), input("이후원", 2L, "2026-001")), HttpStatus.CONFLICT);
        assertThat(service.update(b.id(), input("이후원", 2L, null)).receiptNo()).isNull();
        assertStatus(() -> service.create(input("이후원", 1L, "가".repeat(31))), HttpStatus.BAD_REQUEST);

        // 다른 기관은 같은 번호를 쓸 수 있다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertThat(service.create(input("김후원", 1L, "2026-001")).receiptNo()).isEqualTo("2026-001");
        assertThat(a.receiptNo()).isEqualTo("2026-001");
    }
}
