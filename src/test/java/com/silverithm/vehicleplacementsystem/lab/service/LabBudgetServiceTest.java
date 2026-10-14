package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBudgetDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBudgetLineRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBudgetRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabBudgetServiceTest {

    @Autowired private LabBudgetService service;
    @Autowired private LabBudgetRepository budgets;
    @Autowired private LabBudgetLineRepository lines;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    private static LabBudgetDto.LineInput line(String direction, String code, long amount) {
        return new LabBudgetDto.LineInput(direction, code, amount, null);
    }

    private LabBudgetDto.Input input(int year, int revision, LabBudgetDto.LineInput... lineInputs) {
        return new LabBudgetDto.Input(year, revision, null, "메모", List.of(lineInputs));
    }

    @Test
    void upsertReplacesLines() {
        LabBudgetDto.View first = service.upsert(input(2026, 0, line("EXPENSE", "510", 5000),
                line("INCOME", "401", 9000), line("INCOME", "1010", 100)));
        // 수입 먼저, 코드 오름차순
        assertThat(first.lines()).extracting(l -> l.direction() + l.accountCode())
                .containsExactly("INCOME1010", "INCOME401", "EXPENSE510");
        assertThat(first.status()).isEqualTo("DRAFT");

        // 같은 (연도, 차수)로 다시 저장하면 같은 예산이고 줄은 통째로 바뀐다 (같은 계정 코드를 다시 넣어도 충돌 없음)
        LabBudgetDto.View second = service.upsert(input(2026, 0, line("INCOME", "401", 7000),
                line("EXPENSE", "520", 300)));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.lines()).extracting(l -> l.direction() + l.accountCode() + ":" + l.amount())
                .containsExactly("INCOME401:7000", "EXPENSE520:300");
        assertThat(budgets.count()).isEqualTo(1);
        assertThat(lines.count()).isEqualTo(2);

        // 줄을 모두 비워도 저장된다
        assertThat(service.upsert(input(2026, 0)).lines()).isEmpty();
        assertThat(lines.count()).isZero();

        // 0원 줄은 허용, 음수·한도 초과는 400
        assertThat(service.upsert(input(2026, 0, line("INCOME", "401", 0))).lines().get(0).amount()).isZero();
        assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", "401", -1))), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", "401", 10_000_000_001L))),
                HttpStatus.BAD_REQUEST, null);
        // 실패한 저장은 기존 줄을 건드리지 않는다
        assertThat(lines.count()).isEqualTo(1);

        // 줄 300개까지, 301개는 400
        List<LabBudgetDto.LineInput> many = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            many.add(line("EXPENSE", String.valueOf(100 + i), i));
        }
        assertThat(service.upsert(new LabBudgetDto.Input(2026, 0, null, null, many)).lines()).hasSize(300);
        many.add(line("INCOME", "999", 1));
        assertRejected(() -> service.upsert(new LabBudgetDto.Input(2026, 0, null, null, many)),
                HttpStatus.BAD_REQUEST, "300줄");
        assertThat(lines.count()).isEqualTo(300);

        assertThat(service.listByYear("2026")).hasSize(1);
        assertThat(service.listByYear("2025")).isEmpty();
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertThat(service.listByYear("2026")).isEmpty();
        assertThat(service.upsert(input(2026, 0, line("INCOME", "401", 1))).id()).isNotEqualTo(first.id());
    }

    @Test
    void duplicateAccountRejected() {
        assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", "401", 1), line("INCOME", "401", 2))),
                HttpStatus.BAD_REQUEST, "두 번");
        // 방향이 다르면 같은 코드도 괜찮다
        assertThat(service.upsert(input(2026, 0, line("INCOME", "401", 1), line("EXPENSE", "401", 2))).lines())
                .hasSize(2);
        // 앞뒤 공백이 있어도 같은 코드로 본다
        assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", "401", 1), line("INCOME", " 401 ", 2))),
                HttpStatus.BAD_REQUEST, "두 번");
        assertThat(lines.count()).isEqualTo(2);
    }

    @Test
    void confirmedBudgetLocked() {
        LabBudgetDto.View draft = service.upsert(input(2026, 0, line("INCOME", "401", 100)));
        LabBudgetDto.View confirmed = service.confirm(draft.id());
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedAt()).isNotNull();
        assertThat(confirmed.lines()).hasSize(1);

        String locked = "확정된 예산입니다. 확정을 풀고 고쳐 주세요";
        assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", "401", 999))), HttpStatus.CONFLICT, locked);
        assertRejected(() -> service.delete(draft.id()), HttpStatus.CONFLICT, locked);
        assertThat(service.listByYear("2026").get(0).lines().get(0).amount()).isEqualTo(100);
        // 확정 상태에서 다시 확정해도 그대로
        assertThat(service.confirm(draft.id()).confirmedAt()).isEqualTo(confirmed.confirmedAt());
        // 다른 기관은 확정할 수 없다
        access.companyId = forest.getId() + 1000;
        assertRejected(() -> service.confirm(draft.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void unconfirmReopens() {
        LabBudgetDto.View draft = service.upsert(input(2026, 0, line("INCOME", "401", 100)));
        service.confirm(draft.id());
        LabBudgetDto.View reopened = service.unconfirm(draft.id());
        assertThat(reopened.status()).isEqualTo("DRAFT");
        assertThat(reopened.confirmedAt()).isNull();

        assertThat(service.upsert(input(2026, 0, line("INCOME", "401", 555))).lines().get(0).amount()).isEqualTo(555);
        service.delete(draft.id());
        assertThat(budgets.count()).isZero();
        assertThat(lines.count()).isZero();
        assertRejected(() -> service.unconfirm(draft.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void supplementaryNeedsPreviousRevision() {
        assertRejected(() -> service.upsert(input(2026, 1, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, "본예산");
        service.upsert(input(2026, 0, line("INCOME", "401", 1)));
        assertThat(service.upsert(input(2026, 1, line("INCOME", "401", 2))).revision()).isEqualTo(1);
        // 2차는 1차가 있어야 하고 다른 해의 본예산은 근거가 되지 않는다
        assertRejected(() -> service.upsert(input(2026, 3, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, "2차");
        assertRejected(() -> service.upsert(input(2027, 1, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, "본예산");
        assertThat(service.upsert(input(2026, 2, line("INCOME", "401", 3))).revision()).isEqualTo(2);
        // 차수는 0~9, 연도는 2000~2100
        assertRejected(() -> service.upsert(input(2026, 10, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(2026, -1, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(1999, 0, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, null);
        // 기관마다 따로 — 다른 기관의 본예산은 근거가 아니다
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.upsert(input(2026, 1, line("INCOME", "401", 1))), HttpStatus.BAD_REQUEST, "본예산");
        assertThat(service.listByYear("2026")).isEmpty();
        access.companyId = forest.getId();
        assertThat(service.listByYear("2026")).extracting(LabBudgetDto.View::revision).containsExactly(0, 1, 2);
    }

    @Test
    void deleteRejectedWhenLaterRevisionExists() {
        LabBudgetDto.View base = service.upsert(input(2026, 0, line("INCOME", "401", 1)));
        LabBudgetDto.View supp1 = service.upsert(input(2026, 1, line("INCOME", "401", 2)));
        LabBudgetDto.View supp2 = service.upsert(input(2026, 2, line("INCOME", "401", 3)));

        assertRejected(() -> service.delete(base.id()), HttpStatus.CONFLICT, "뒤 차수");
        assertRejected(() -> service.delete(supp1.id()), HttpStatus.CONFLICT, "뒤 차수");
        assertThat(budgets.count()).isEqualTo(3);

        service.delete(supp2.id());
        service.delete(supp1.id());
        service.delete(base.id());
        assertThat(budgets.count()).isZero();
        assertThat(lines.count()).isZero();
        // 다른 해의 차수는 상관없다
        LabBudgetDto.View y2026 = service.upsert(input(2026, 0, line("INCOME", "401", 1)));
        service.upsert(input(2027, 0, line("INCOME", "401", 1)));
        service.delete(y2026.id());
        assertThat(service.listByYear("2027")).hasSize(1);
    }

    @Test
    void accountCodeValidated() {
        for (String ok : List.of("101", "4010", "000")) {
            assertThat(service.upsert(input(2026, 0, line("INCOME", ok, 1))).lines().get(0).accountCode()).isEqualTo(ok);
        }
        for (String bad : new String[] {"10", "12345", "4a1", "", "  ", null, "４０１"}) {
            assertRejected(() -> service.upsert(input(2026, 0, line("INCOME", bad, 1))), HttpStatus.BAD_REQUEST,
                    "계정 코드");
        }
        assertRejected(() -> service.upsert(input(2026, 0, line("SALES", "401", 1))), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.upsert(input(2026, 0, new LabBudgetDto.LineInput("INCOME", "401", null, null))),
                HttpStatus.BAD_REQUEST, null);
        // 실패한 저장 뒤에도 마지막 성공 저장이 남아 있다
        assertThat(service.listByYear("2026").get(0).lines()).extracting(LabBudgetDto.LineView::accountCode)
                .containsExactly("000");
    }
}
