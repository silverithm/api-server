package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabBudgetDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBudget;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBudgetLine;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBudgetLineRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBudgetRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예산(본예산·추경) — 기관 x 회계연도 x 차수 1건(PUT은 upsert)에 (방향, 계정 코드)별 줄이 붙는다.
 *
 * <p>줄은 저장할 때마다 통째로 바꾼다(부분 수정 없음). 확정(CONFIRMED)된 예산은 고치거나 지울 수 없고(409),
 * 확정을 풀어야 한다. 추경(차수 1 이상)은 같은 해 바로 앞 차수가 있어야 만들 수 있고, 뒤 차수가 있는 예산은
 * 지울 수 없다 — 차수가 중간에 비면 추경의 근거가 사라지기 때문이다. 세입·세출 균형은 서버가 막지 않는다.
 */
@Service
public class LabBudgetService {

    static final int MAX_LINES = 300;
    static final int MAX_REVISION = 9;
    private static final long MAX_AMOUNT = 10_000_000_000L;
    private static final int NOTE = 500;
    private static final int LINE_NOTE = 200;
    private static final Pattern ACCOUNT_CODE = Pattern.compile("^[0-9]{3,4}$");
    private static final Set<String> DIRECTIONS = Set.of("INCOME", "EXPENSE");
    private static final String LOCKED = "확정된 예산입니다. 확정을 풀고 고쳐 주세요";

    private final LabAccessService access;
    private final LabBudgetRepository budgets;
    private final LabBudgetLineRepository lines;
    private final TransactionTemplate tx;

    public LabBudgetService(LabAccessService access, LabBudgetRepository budgets, LabBudgetLineRepository lines,
                            PlatformTransactionManager txManager) {
        this.access = access;
        this.budgets = budgets;
        this.lines = lines;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 그 해 예산 전부(차수 오름차순, 줄 포함). 줄은 한 번의 쿼리로 읽는다 */
    @Transactional(readOnly = true)
    public List<LabBudgetDto.View> listByYear(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabBudget> rows = budgets.findByCompanyIdAndFiscalYearOrderByRevisionAsc(companyId, (short) y);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<LabBudgetLine>> byBudget = lines.findByBudgetIdIn(rows.stream().map(LabBudget::getId).toList())
                .stream().collect(Collectors.groupingBy(LabBudgetLine::getBudgetId));
        return rows.stream().map(b -> toView(b, byBudget.getOrDefault(b.getId(), List.of()))).toList();
    }

    /** 연도·차수로 저장하거나 고친다. 줄은 통째로 바뀐다 */
    public LabBudgetDto.View upsert(LabBudgetDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("예산 내용을 보내 주세요");
        }
        short year = (short) LabValidation.year(input.fiscalYear(), "회계연도");
        short revision = revision(input.revision());
        var decidedOn = input.decidedOn() == null ? null : LabValidation.plausibleDate(input.decidedOn(), "의결일");
        String note = LabValidation.text(input.note(), NOTE, "메모");
        List<LabBudgetDto.LineInput> lineInputs = input.lines() == null ? List.of() : input.lines();
        if (lineInputs.size() > MAX_LINES) {
            throw LabValidation.bad("예산 줄은 최대 " + MAX_LINES + "줄까지 넣을 수 있습니다");
        }
        List<LabBudgetLine> newLines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LabBudgetDto.LineInput line : lineInputs) {
            newLines.add(toLine(line, companyId, seen));
        }

        return LabValidation.upsertWithRetry(tx, () -> {
            LabBudget budget = budgets.findByCompanyIdAndFiscalYearAndRevision(companyId, year, revision)
                    .orElse(null);
            if (budget == null) {
                // 추경은 같은 해 바로 앞 차수가 있어야 한다
                if (revision > 0 && budgets.findByCompanyIdAndFiscalYearAndRevision(
                        companyId, year, (short) (revision - 1)).isEmpty()) {
                    throw LabValidation.bad(revision == 1 ? "추경은 본예산이 있어야 만들 수 있습니다"
                            : "추경 " + revision + "차는 추경 " + (revision - 1) + "차가 있어야 만들 수 있습니다");
                }
                budget = new LabBudget(companyId, year, revision);
            } else if (budget.isConfirmed()) {
                throw new CustomException(LOCKED, HttpStatus.CONFLICT);
            }
            budget.fill(decidedOn, note);
            LabBudget saved = budgets.saveAndFlush(budget);

            // 줄 통째 교체: 유일 키(예산, 방향, 코드)와 겹치지 않게 지우기를 먼저 반영(flush)한 뒤 넣는다.
            // Hibernate는 한 번의 flush에서 INSERT를 DELETE보다 먼저 보내기 때문이다
            lines.deleteAll(lines.findByBudgetId(saved.getId()));
            lines.flush();
            List<LabBudgetLine> fresh = newLines.stream()
                    .map(l -> new LabBudgetLine(saved.getId(), companyId, l.getDirection(), l.getAccountCode(),
                            l.getAmount(), l.getNote()))
                    .toList();
            return toView(saved, lines.saveAllAndFlush(fresh));
        });
    }

    @Transactional
    public LabBudgetDto.View confirm(Long id) {
        LabBudget budget = find(id);
        if (!budget.isConfirmed()) {
            budget.confirm();
            budget.touch();
            budgets.saveAndFlush(budget);
        }
        return toView(budget, lines.findByBudgetId(budget.getId()));
    }

    @Transactional
    public LabBudgetDto.View unconfirm(Long id) {
        LabBudget budget = find(id);
        if (budget.isConfirmed()) {
            budget.reopen();
            budget.touch();
            budgets.saveAndFlush(budget);
        }
        return toView(budget, lines.findByBudgetId(budget.getId()));
    }

    /** 초안만 지울 수 있고, 뒤 차수 예산이 있으면 지울 수 없다(둘 다 409). 줄은 DB 연쇄 삭제가 아닌 서비스가 함께 지운다 */
    @Transactional
    public void delete(Long id) {
        LabBudget budget = find(id);
        if (budget.isConfirmed()) {
            throw new CustomException(LOCKED, HttpStatus.CONFLICT);
        }
        if (budgets.existsByCompanyIdAndFiscalYearAndRevisionGreaterThan(
                budget.getCompanyId(), budget.getFiscalYear(), budget.getRevision())) {
            throw new CustomException("뒤 차수 예산이 있어 지울 수 없습니다. 뒤 차수부터 지워 주세요",
                    HttpStatus.CONFLICT);
        }
        lines.deleteAll(lines.findByBudgetId(budget.getId()));
        lines.flush();
        budgets.delete(budget);
    }

    private LabBudget find(Long id) {
        Long companyId = access.requireCompanyId();
        return budgets.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("예산을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
    }

    private static short revision(Integer value) {
        if (value == null) {
            throw LabValidation.bad("차수를 입력해 주세요");
        }
        if (value < 0 || value > MAX_REVISION) {
            throw LabValidation.bad("차수는 0(본예산)~" + MAX_REVISION + "(추경 " + MAX_REVISION + "차) 사이여야 합니다");
        }
        return value.shortValue();
    }

    /** 줄 하나 검증 — 같은 (방향, 코드)가 두 번 나오면 400 */
    private static LabBudgetLine toLine(LabBudgetDto.LineInput line, Long companyId, Set<String> seen) {
        if (line == null) {
            throw LabValidation.bad("예산 줄에 빈 값이 있습니다");
        }
        String direction = LabValidation.oneOf(line.direction(), DIRECTIONS, "구분");
        String code = line.accountCode() == null ? null : line.accountCode().strip();
        if (code == null || !ACCOUNT_CODE.matcher(code).matches()) {
            throw LabValidation.bad("계정 코드는 3~4자리 숫자여야 합니다");
        }
        if (!seen.add(direction + ":" + code)) {
            throw LabValidation.bad("같은 구분·계정 코드가 두 번 있습니다: " + code);
        }
        long amount = LabValidation.longInRange(line.amount(), 0, MAX_AMOUNT, "금액");
        String note = LabValidation.text(line.note(), LINE_NOTE, "줄 메모");
        return new LabBudgetLine(null, companyId, direction, code, amount, note);
    }

    /** 수입 먼저, 계정 코드 오름차순 */
    private static LabBudgetDto.View toView(LabBudget b, List<LabBudgetLine> lineRows) {
        List<LabBudgetDto.LineView> views = lineRows.stream()
                .sorted(Comparator.comparing((LabBudgetLine l) -> "INCOME".equals(l.getDirection()) ? 0 : 1)
                        .thenComparing(LabBudgetLine::getAccountCode))
                .map(l -> new LabBudgetDto.LineView(l.getDirection(), l.getAccountCode(), l.getAmount(), l.getNote()))
                .toList();
        return new LabBudgetDto.View(b.getId(), b.getFiscalYear(), b.getRevision(), b.getDecidedOn(),
                b.getStatus(), b.getNote(), b.getConfirmedAt(), views, b.getUpdatedAt());
    }
}
