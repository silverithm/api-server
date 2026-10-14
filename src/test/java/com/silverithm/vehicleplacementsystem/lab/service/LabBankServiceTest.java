package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBankDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBankAccountRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBankStatementRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabBankServiceTest {

    @Autowired private LabBankService service;
    @Autowired private LabBankAccountRepository accounts;
    @Autowired private LabBankStatementRepository statements;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private static LabBankDto.AccountInput account(String name, String purpose, String tail) {
        return new LabBankDto.AccountInput(name, purpose, "농협", tail, 1_000_000L, LocalDate.of(2026, 1, 1), null);
    }

    private static LabBankDto.StatementInput statement(Long accountId, String month, Long closing) {
        return new LabBankDto.StatementInput(accountId, month, closing, null);
    }

    // 끝 4자리 숫자만 저장 — 전체 번호·숫자 아닌 값은 400이고 어디에도 남지 않는다
    @Test
    void accountTailOnlyFourDigits() {
        LabBankDto.AccountView ok = service.createAccount(account("운영 통장", "OPERATING", "1234"));
        assertThat(ok.accountTail()).isEqualTo("1234");
        assertThat(ok.active()).isTrue();
        assertThat(service.createAccount(account("후원", "DONATION", null)).accountTail()).isNull();
        assertThat(service.createAccount(account("후원2", "DONATION", "  ")).accountTail()).isNull();

        for (String bad : List.of("123", "12345", "12ab", "110-123-456789", "１２３４", "-123")) {
            assertRejected(() -> service.createAccount(account("나쁨", "OPERATING", bad)), HttpStatus.BAD_REQUEST,
                    "끝 4자리");
        }
        assertRejected(() -> service.updateAccount(ok.id(), account("운영 통장", "OPERATING", "110123456789")),
                HttpStatus.BAD_REQUEST, "끝 4자리");
        assertThat(accounts.count()).isEqualTo(3);
        // 저장된 열 어디에도 전체 번호가 없다
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lab_bank_accounts WHERE account_tail IS NOT NULL "
                + "AND LENGTH(account_tail) <> 4", Integer.class)).isZero();
        assertThat(accounts.findById(ok.id()).orElseThrow().getAccountTail()).isEqualTo("1234");

        // 나머지 입력 규칙
        assertRejected(() -> service.createAccount(account("x", "SAVINGS", "1234")), HttpStatus.BAD_REQUEST, "용도");
        assertRejected(() -> service.createAccount(account(" ", "OTHER", "1234")), HttpStatus.BAD_REQUEST, "통장 이름");
        assertRejected(() -> service.createAccount(new LabBankDto.AccountInput("x", "OTHER", "농협", "1234",
                10_000_000_001L, LocalDate.of(2026, 1, 1), null)), HttpStatus.BAD_REQUEST, "기초 잔액");
        assertRejected(() -> service.createAccount(new LabBankDto.AccountInput("x", "OTHER", "농협", "1234",
                -10_000_000_001L, LocalDate.of(2026, 1, 1), null)), HttpStatus.BAD_REQUEST, "기초 잔액");
        assertRejected(() -> service.createAccount(new LabBankDto.AccountInput("x", "OTHER", "농협", "1234",
                null, LocalDate.of(2026, 1, 1), null)), HttpStatus.BAD_REQUEST, "기초 잔액");
        assertRejected(() -> service.createAccount(new LabBankDto.AccountInput("x", "OTHER", "농협", "1234", 0L,
                null, null)), HttpStatus.BAD_REQUEST, "기준일");
        assertThat(service.createAccount(new LabBankDto.AccountInput("경계", "OTHER", "농협", null,
                -10_000_000_000L, LocalDate.of(2026, 1, 1), false)).active()).isFalse();
        assertThat(service.updateAccount(ok.id(), account("이름 바꿈", "DEPOSIT", "9876")).purpose())
                .isEqualTo("DEPOSIT");
        assertThat(service.listAccounts()).extracting(LabBankDto.AccountView::name)
                .containsExactly("이름 바꿈", "후원", "후원2", "경계");
    }

    // 통장 x 달 한 건 — 다시 저장하면 덮어쓰고, 통장 이름이 채워지며, 월·금액 범위를 지킨다
    @Test
    void statementUpsertByAccountMonth() {
        LabBankDto.AccountView a = service.createAccount(account("운영 통장", "OPERATING", "1234"));
        LabBankDto.AccountView b = service.createAccount(account("후원 통장", "DONATION", "5678"));

        LabBankDto.StatementView first = service.upsertStatement(statement(a.id(), "2026-03", 5_000_000L));
        assertThat(first.accountName()).isEqualTo("운영 통장");
        LabBankDto.StatementView second = service.upsertStatement(new LabBankDto.StatementInput(a.id(), "2026-03",
                -300L, "수정"));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.closingBalance()).isEqualTo(-300L);
        assertThat(second.note()).isEqualTo("수정");
        assertThat(statements.count()).isEqualTo(1);
        // 다른 통장·다른 달은 따로
        service.upsertStatement(statement(b.id(), "2026-03", 10L));
        service.upsertStatement(statement(a.id(), "2026-04", 20L));
        assertThat(statements.count()).isEqualTo(3);

        assertThat(service.listStatements("2026-03", null)).extracting(LabBankDto.StatementView::accountName)
                .containsExactly("운영 통장", "후원 통장");
        assertThat(service.listStatements(null, a.id())).extracting(LabBankDto.StatementView::statementMonth)
                .containsExactly("2026-03", "2026-04");
        assertThat(service.listStatements("2026-04", a.id())).hasSize(1);
        assertThat(service.listStatements("2026-04", b.id())).isEmpty();
        assertRejected(() -> service.listStatements(null, null), HttpStatus.BAD_REQUEST, "month 또는 accountId");
        assertRejected(() -> service.listStatements("2026-13", null), HttpStatus.BAD_REQUEST, "YYYY-MM");

        assertRejected(() -> service.upsertStatement(statement(a.id(), "2026-3", 1L)), HttpStatus.BAD_REQUEST,
                "YYYY-MM");
        assertRejected(() -> service.upsertStatement(statement(a.id(), "2026-03", null)), HttpStatus.BAD_REQUEST,
                "월말 잔액");
        assertRejected(() -> service.upsertStatement(statement(a.id(), "2026-03", 10_000_000_001L)),
                HttpStatus.BAD_REQUEST, "월말 잔액");
        assertRejected(() -> service.upsertStatement(statement(null, "2026-03", 1L)), HttpStatus.BAD_REQUEST, "통장");
        assertThat(service.listStatements("2026-03", a.id()).get(0).closingBalance()).isEqualTo(-300L);

        service.deleteStatement(first.id());
        assertThat(statements.count()).isEqualTo(2);
        assertRejected(() -> service.deleteStatement(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
    }

    // 월말 잔액이 한 건이라도 있으면 통장 삭제는 409 — 기록이 남아 있고, 지운 뒤에는 삭제된다
    @Test
    void accountWithStatementsDeleteRejected() {
        LabBankDto.AccountView a = service.createAccount(account("운영 통장", "OPERATING", "1234"));
        LabBankDto.AccountView empty = service.createAccount(account("빈 통장", "OTHER", null));
        LabBankDto.StatementView s = service.upsertStatement(statement(a.id(), "2026-03", 100L));

        assertRejected(() -> service.deleteAccount(a.id()), HttpStatus.CONFLICT, "월말 잔액 기록이 있는 통장");
        assertThat(accounts.findById(a.id())).isPresent();
        assertThat(statements.findById(s.id())).isPresent();

        service.deleteAccount(empty.id());
        assertThat(accounts.findById(empty.id())).isEmpty();

        service.deleteStatement(s.id());
        service.deleteAccount(a.id());
        assertThat(accounts.count()).isZero();
        assertRejected(() -> service.deleteAccount(a.id()), HttpStatus.NOT_FOUND, "통장을 찾을 수 없습니다");
    }

    // 다른 기관 통장에는 잔액을 넣을 수도, 보거나 지우거나 고칠 수도 없다
    @Test
    void statementAccountMustBeSameCompany() {
        LabBankDto.AccountView mine = service.createAccount(account("운영 통장", "OPERATING", "1234"));
        LabBankDto.StatementView s = service.upsertStatement(statement(mine.id(), "2026-03", 100L));

        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        assertRejected(() -> service.upsertStatement(statement(mine.id(), "2026-03", 999L)), HttpStatus.NOT_FOUND,
                "통장을 찾을 수 없습니다");
        assertRejected(() -> service.upsertStatement(statement(999_999L, "2026-03", 1L)), HttpStatus.NOT_FOUND,
                "통장을 찾을 수 없습니다");
        assertRejected(() -> service.listStatements(null, mine.id()), HttpStatus.NOT_FOUND, "통장을 찾을 수 없습니다");
        assertThat(service.listStatements("2026-03", null)).isEmpty();
        assertThat(service.listAccounts()).isEmpty();
        assertRejected(() -> service.deleteStatement(s.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        assertRejected(() -> service.updateAccount(mine.id(), account("탈취", "OTHER", "0000")),
                HttpStatus.NOT_FOUND, "통장을 찾을 수 없습니다");
        assertRejected(() -> service.deleteAccount(mine.id()), HttpStatus.NOT_FOUND, "통장을 찾을 수 없습니다");

        // 내 것은 그대로
        assertThat(statements.findById(s.id()).orElseThrow().getClosingBalance()).isEqualTo(100L);
        assertThat(accounts.findById(mine.id()).orElseThrow().getName()).isEqualTo("운영 통장");
        assertThat(statements.count()).isEqualTo(1);
    }
}
