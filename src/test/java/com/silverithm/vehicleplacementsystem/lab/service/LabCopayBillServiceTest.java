package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto.NonCoveredItem;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCopayBillRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabCopayBillServiceTest {

    @Autowired private LabCopayBillService service;
    @Autowired private LabCopayBillRepository bills;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Elderly kim;

    @BeforeEach
    void setUp() {
        Company forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private LabCopayBillDto.Input input(int copay, int overLimit, List<NonCoveredItem> items, int previous) {
        return new LabCopayBillDto.Input(kim.getId(), "2026-10", "3", "standard", "general", "h8to10", 22,
                60000, 1320000, copay, overLimit, items, previous);
    }

    private List<NonCoveredItem> meals() {
        return List.of(new NonCoveredItem("식비", 3000), new NonCoveredItem("간식비", 2000));
    }

    // 청구액은 본인부담금+한도초과+비급여+전월미수금을 서버가 다시 더해 정한다
    @Test
    void totalDueComputedFromParts() {
        LabCopayBillDto.View view = service.upsert(input(100000, 5000, meals(), 10000));

        assertThat(view.nonCoveredAmount()).isEqualTo(5000);
        assertThat(view.totalDue()).isEqualTo(120000);
        assertThat(view.paidAmount()).isZero();
        assertThat(view.status()).isEqualTo("UNPAID");
        assertThat(view.nonCoveredItems()).extracting(NonCoveredItem::label).containsExactly("식비", "간식비");
        assertThat(view.elderlyName()).isEqualTo("김순자");

        // 청구 0원이면 낼 돈이 없으므로 완납으로 본다
        LabCopayBillDto.View zero = service.upsert(new LabCopayBillDto.Input(kim.getId(), "2026-11", "3",
                "medicalAid", "general", "h8to10", 0, 0, 0, 0, null, null, null));
        assertThat(zero.totalDue()).isZero();
        assertThat(zero.status()).isEqualTo("PAID");
    }

    // 수납은 누적되고, 일부면 PARTIAL, 청구액 이상이면 PAID
    @Test
    void paymentsMoveStatusToPartialThenPaid() {
        LabCopayBillDto.View bill = service.upsert(input(100000, 5000, meals(), 10000));

        LabCopayBillDto.View partial = service.addPayment(bill.id(),
                new LabCopayBillDto.PaymentInput(50000, LocalDate.of(2026, 10, 5), "김철수"));
        assertThat(partial.paidAmount()).isEqualTo(50000);
        assertThat(partial.status()).isEqualTo("PARTIAL");
        assertThat(partial.lastPaidOn()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(partial.payerName()).isEqualTo("김철수");

        LabCopayBillDto.View paid = service.addPayment(bill.id(),
                new LabCopayBillDto.PaymentInput(70000, LocalDate.of(2026, 10, 20), null));
        assertThat(paid.paidAmount()).isEqualTo(120000);
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.lastPaidOn()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(paid.payerName()).isEqualTo("김철수");
    }

    // 청구서를 다시 저장해도 이미 받은 금액은 지워지지 않고 상태만 새 청구액 기준으로 바뀐다
    @Test
    void resaveKeepsPaidAmount() {
        LabCopayBillDto.View bill = service.upsert(input(100000, 0, null, 0));
        service.addPayment(bill.id(), new LabCopayBillDto.PaymentInput(100000, null, null));

        LabCopayBillDto.View resaved = service.upsert(input(100000, 0, meals(), 0));

        assertThat(resaved.id()).isEqualTo(bill.id());
        assertThat(bills.count()).isEqualTo(1);
        assertThat(resaved.paidAmount()).isEqualTo(100000);
        assertThat(resaved.totalDue()).isEqualTo(105000);
        assertThat(resaved.status()).isEqualTo("PARTIAL");
    }

    // 음수·한도 밖 금액과 잘못된 일수·항목은 400
    @Test
    void negativeAmountRejected() {
        assertBad(() -> service.upsert(input(-1, 0, null, 0)));
        assertBad(() -> service.upsert(input(0, 0, List.of(new NonCoveredItem("식비", -500)), 0)));
        assertBad(() -> service.upsert(input(0, 0, null, -1)));
        assertBad(() -> service.upsert(input(100_000_001, 0, null, 0)));
        assertBad(() -> service.upsert(new LabCopayBillDto.Input(kim.getId(), "2026-10", "3", "standard",
                "general", "h8to10", 32, 0, 0, 0, 0, null, 0)));
        assertBad(() -> service.upsert(new LabCopayBillDto.Input(kim.getId(), "2026-10", "9", "standard",
                "general", "h8to10", 20, 0, 0, 0, 0, null, 0)));
        assertBad(() -> service.upsert(input(0, 0, List.of(new NonCoveredItem("가".repeat(41), 100)), 0)));

        List<NonCoveredItem> tooMany = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(new NonCoveredItem("항목" + i, 1));
        }
        assertBad(() -> service.upsert(input(0, 0, tooMany, 0)));

        LabCopayBillDto.View bill = service.upsert(input(1000, 0, null, 0));
        assertBad(() -> service.addPayment(bill.id(), new LabCopayBillDto.PaymentInput(0, null, null)));
        assertBad(() -> service.addPayment(bill.id(), new LabCopayBillDto.PaymentInput(-5, null, null)));
        assertBad(() -> service.addPayment(bill.id(), new LabCopayBillDto.PaymentInput(100_000_001, null, null)));
        assertThat(bills.count()).isEqualTo(1);
    }

    // 다른 기관 어르신의 청구서는 만들 수 없고, 다른 기관 청구서에는 수납도 못 한다
    @Test
    void tenancy() {
        LabCopayBillDto.View bill = service.upsert(input(1000, 0, null, 0));
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertThatThrownBy(() -> service.upsert(new LabCopayBillDto.Input(stranger.getId(), "2026-10", "3",
                "standard", "general", "h8to10", 20, 0, 0, 0, 0, null, 0)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        access.companyId = other.getId();
        assertThatThrownBy(() -> service.addPayment(bill.id(), new LabCopayBillDto.PaymentInput(1, null, null)))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static void assertBad(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
