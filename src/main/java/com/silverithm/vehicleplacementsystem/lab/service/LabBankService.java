package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBankDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBankAccount;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBankStatement;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBankAccountRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBankStatementRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기관 통장과 월말 잔액 — 장부 잔액과 통장 잔액을 맞춰 보는 대사의 입력.
 *
 * <p>계좌번호는 끝 4자리만 받는다(전체 번호는 받을 입력도 저장할 열도 없다). 월말 잔액이 한 건이라도 있는
 * 통장은 지울 수 없다(409) — DB 연쇄 삭제가 대사 기록을 조용히 없애지 않게 서비스가 먼저 막는다.
 * 통장 행을 잠그고 확인해 "기록이 없다고 보고 지우는 순간 새 잔액이 들어오는" 경합도 막는다.
 */
@Service
public class LabBankService {

    static final Set<String> PURPOSES = Set.of("OPERATING", "DONATION", "DEPOSIT", "OTHER");
    private static final Pattern TAIL = Pattern.compile("^\\d{4}$");
    private static final long MAX_BALANCE = 10_000_000_000L;

    private final LabAccessService access;
    private final LabBankAccountRepository accounts;
    private final LabBankStatementRepository statements;
    private final TransactionTemplate tx;

    public LabBankService(LabAccessService access, LabBankAccountRepository accounts,
                          LabBankStatementRepository statements, PlatformTransactionManager txManager) {
        this.access = access;
        this.accounts = accounts;
        this.statements = statements;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---- 통장 ----

    @Transactional(readOnly = true)
    public List<LabBankDto.AccountView> listAccounts() {
        Long companyId = access.requireCompanyId();
        return accounts.findByCompanyIdOrderByActiveDescIdAsc(companyId).stream()
                .map(LabBankService::accountView).toList();
    }

    @Transactional
    public LabBankDto.AccountView createAccount(LabBankDto.AccountInput input) {
        Long companyId = access.requireCompanyId();
        LabBankAccount account = new LabBankAccount(companyId);
        applyAccount(account, input);
        return accountView(accounts.saveAndFlush(account));
    }

    @Transactional
    public LabBankDto.AccountView updateAccount(Long id, LabBankDto.AccountInput input) {
        Long companyId = access.requireCompanyId();
        LabBankAccount account = accounts.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabBankService::accountNotFound);
        applyAccount(account, input);
        return accountView(accounts.saveAndFlush(account));
    }

    @Transactional
    public void deleteAccount(Long id) {
        Long companyId = access.requireCompanyId();
        LabBankAccount account = accounts.lockByIdAndCompanyId(id, companyId)
                .orElseThrow(LabBankService::accountNotFound);
        if (statements.existsByAccountId(account.getId())) {
            throw new CustomException("월말 잔액 기록이 있는 통장은 지울 수 없습니다. 월말 잔액 기록을 먼저 지우거나 통장을 '사용 안 함'으로 바꿔 주세요",
                    HttpStatus.CONFLICT);
        }
        accounts.delete(account);
    }

    private static void applyAccount(LabBankAccount account, LabBankDto.AccountInput input) {
        if (input == null) {
            throw LabValidation.bad("통장 내용을 보내 주세요");
        }
        String name = LabValidation.requiredText(input.name(), 50, "통장 이름");
        String purpose = LabValidation.oneOf(input.purpose(), PURPOSES, "용도");
        String bankName = LabValidation.requiredText(input.bankName(), 30, "은행 이름");
        long opening = LabValidation.longInRange(input.openingBalance(), -MAX_BALANCE, MAX_BALANCE, "기초 잔액");
        LocalDate openingDate = LabValidation.plausibleDate(input.openingDate(), "기초 잔액 기준일");
        boolean active = input.active() == null || input.active();
        account.fill(name, purpose, bankName, tail(input.accountTail()), opening, openingDate, active);
    }

    /** 끝 4자리 숫자만. 비면 null. 전체 번호를 붙여 넣은 경우도 4자리가 아니니 400이고 값은 저장되지 않는다 */
    private static String tail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (!TAIL.matcher(text).matches()) {
            throw LabValidation.bad("계좌번호는 끝 4자리 숫자만 입력해 주세요. 전체 번호는 저장하지 않습니다");
        }
        return text;
    }

    // ---- 월말 잔액 ----

    /** month 또는 accountId 필터(둘 다 주면 둘 다 만족하는 것). 둘 다 없으면 400 */
    @Transactional(readOnly = true)
    public List<LabBankDto.StatementView> listStatements(String month, Long accountId) {
        Long companyId = access.requireCompanyId();
        if (month == null && accountId == null) {
            throw LabValidation.bad("month 또는 accountId 중 하나를 보내 주세요");
        }
        String m = month == null ? null : LabValidation.month(month, "월");
        if (accountId != null) {
            accounts.findByIdAndCompanyId(accountId, companyId).orElseThrow(LabBankService::accountNotFound);
        }
        List<LabBankStatement> rows;
        if (accountId != null && m != null) {
            rows = statements.findByCompanyIdAndAccountIdAndStatementMonthOrderByIdAsc(companyId, accountId, m);
        } else if (accountId != null) {
            rows = statements.findByCompanyIdAndAccountIdOrderByStatementMonthAsc(companyId, accountId);
        } else {
            rows = statements.findByCompanyIdAndStatementMonthOrderByAccountIdAsc(companyId, m);
        }
        Map<Long, String> names = new HashMap<>();
        accounts.findAllById(rows.stream().map(LabBankStatement::getAccountId).collect(
                java.util.stream.Collectors.toSet())).stream().filter(a -> companyId.equals(a.getCompanyId()))
                .forEach(a -> names.put(a.getId(), a.getName()));
        return rows.stream().map(r -> statementView(r, names.get(r.getAccountId()))).toList();
    }

    public LabBankDto.StatementView upsertStatement(LabBankDto.StatementInput input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("월말 잔액을 보내 주세요");
        }
        if (input.accountId() == null) {
            throw LabValidation.bad("통장을 선택해 주세요");
        }
        String month = LabValidation.month(input.statementMonth(), "대사 월");
        LabValidation.plausibleDate(java.time.YearMonth.parse(month).atDay(1), "대사 월");
        long closing = LabValidation.longInRange(input.closingBalance(), -MAX_BALANCE, MAX_BALANCE, "월말 잔액");
        String note = LabValidation.text(input.note(), 200, "메모");
        Long accountId = input.accountId();

        return LabValidation.upsertWithRetry(tx, () -> {
            // 다른 기관 통장은 존재를 알려 주지 않고 404. 잠가서 같은 통장의 삭제와 겹치지 않게 한다
            LabBankAccount account = accounts.lockByIdAndCompanyId(accountId, companyId)
                    .orElseThrow(LabBankService::accountNotFound);
            LabBankStatement row = statements.findByAccountIdAndStatementMonth(accountId, month)
                    .orElseGet(() -> new LabBankStatement(companyId, accountId, month));
            row.fill(closing, note);
            return statementView(statements.saveAndFlush(row), account.getName());
        });
    }

    @Transactional
    public void deleteStatement(Long id) {
        Long companyId = access.requireCompanyId();
        statements.delete(statements.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("월말 잔액 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND)));
    }

    private static CustomException accountNotFound() {
        return new CustomException("통장을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    private static LabBankDto.AccountView accountView(LabBankAccount a) {
        return new LabBankDto.AccountView(a.getId(), a.getName(), a.getPurpose(), a.getBankName(),
                a.getAccountTail(), a.getOpeningBalance(), a.getOpeningDate(), a.isActive(), a.getUpdatedAt());
    }

    private static LabBankDto.StatementView statementView(LabBankStatement s, String accountName) {
        return new LabBankDto.StatementView(s.getId(), s.getAccountId(), accountName, s.getStatementMonth(),
                s.getClosingBalance(), s.getNote(), s.getUpdatedAt());
    }
}
