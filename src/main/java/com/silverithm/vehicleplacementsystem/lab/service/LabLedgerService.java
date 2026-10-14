package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabLedgerDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabLedgerEntry;
import com.silverithm.vehicleplacementsystem.lab.repository.LabDonationRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabLedgerEntryRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현금출납부 — 수입·지출 기록의 등록·수정·삭제와 월·연도 조회(날짜, id 순).
 *
 * <p>계정 코드는 3~4자리 숫자, 금액은 1~100억(BIGINT, 0·음수 거절). 후원금 연결(donationId)은 같은 기관 후원금만
 * 되고 아니면 404 — 클라이언트가 보낸 id라 다른 기관 후원금에 지출이 붙으면 그 기관의 사용액이 오염된다.
 * 연결 시 후원금 행을 잠가 후원금 삭제와 엇갈려도 연결이 끊기거나 FK 오류(500)가 나지 않게 한다.
 */
@Service
public class LabLedgerService {

    static final long MAX_AMOUNT = 10_000_000_000L;
    private static final Pattern ACCOUNT_CODE = Pattern.compile("^[0-9]{3,4}$");
    private static final Set<String> DIRECTIONS = Set.of("INCOME", "EXPENSE");
    private static final Set<String> EVIDENCE = Set.of("RECEIPT", "CARD", "TAX_INVOICE", "TRANSFER", "OTHER");

    private final LabAccessService access;
    private final LabLedgerEntryRepository entries;
    private final LabDonationRepository donations;

    public LabLedgerService(LabAccessService access, LabLedgerEntryRepository entries,
                            LabDonationRepository donations) {
        this.access = access;
        this.entries = entries;
        this.donations = donations;
    }

    /** month(YYYY-MM)와 year(YYYY) 중 정확히 하나만 받는다. 날짜·id 순 */
    @Transactional(readOnly = true)
    public List<LabLedgerDto.View> list(String month, String year) {
        Long companyId = access.requireCompanyId();
        boolean hasMonth = month != null && !month.isBlank();
        boolean hasYear = year != null && !year.isBlank();
        if (hasMonth == hasYear) {
            throw LabValidation.bad("month 또는 year 중 하나만 보내 주세요");
        }
        LocalDate from;
        LocalDate to;
        if (hasMonth) {
            YearMonth ym = LabValidation.yearMonth(month, "월");
            LabValidation.year(ym.getYear(), "연도");
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else {
            int y = LabValidation.year(year, "연도");
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }
        return entries.findByCompanyIdAndEntryDateBetweenOrderByEntryDateAscIdAsc(companyId, from, to).stream()
                .map(LabLedgerService::toView).toList();
    }

    @Transactional
    public LabLedgerDto.View create(LabLedgerDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabLedgerEntry entry = new LabLedgerEntry(companyId);
        apply(entry, input, companyId);
        return toView(entries.saveAndFlush(entry));
    }

    @Transactional
    public LabLedgerDto.View update(Long id, LabLedgerDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabLedgerEntry entry = entries.findByIdAndCompanyId(id, companyId).orElseThrow(LabLedgerService::notFound);
        apply(entry, input, companyId);
        return toView(entries.saveAndFlush(entry));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        entries.delete(entries.findByIdAndCompanyId(id, companyId).orElseThrow(LabLedgerService::notFound));
    }

    private void apply(LabLedgerEntry entry, LabLedgerDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("장부 내용을 보내 주세요");
        }
        LocalDate date = LabValidation.plausibleDate(input.entryDate(), "날짜");
        String direction = LabValidation.oneOf(input.direction(), DIRECTIONS, "구분");
        String code = input.accountCode() == null ? null : input.accountCode().strip();
        if (code == null || !ACCOUNT_CODE.matcher(code).matches()) {
            throw LabValidation.bad("계정 코드는 3~4자리 숫자여야 합니다");
        }
        String description = LabValidation.requiredText(input.description(), 200, "적요");
        long amount = LabValidation.longInRange(input.amount(), 1, MAX_AMOUNT, "금액");
        String counterparty = LabValidation.text(input.counterparty(), 100, "거래처");
        String evidence = LabValidation.text(input.evidenceType(), 20, "증빙 종류");
        if (evidence != null && !EVIDENCE.contains(evidence)) {
            throw LabValidation.bad("증빙 종류 값이 올바르지 않습니다");
        }
        Long donationId = input.donationId();
        if (donationId != null) {
            // 같은 기관 후원금만 — 잠가서 읽어 연결 도중 후원금이 지워지지 않게 한다
            donations.findForUpdate(donationId, companyId)
                    .orElseThrow(() -> new CustomException("후원금을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        }
        entry.fill(date, direction, code, description, amount, counterparty, evidence, donationId,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static LabLedgerDto.View toView(LabLedgerEntry e) {
        return new LabLedgerDto.View(e.getId(), e.getEntryDate(), e.getDirection(), e.getAccountCode(),
                e.getDescription(), e.getAmount(), e.getCounterparty(), e.getEvidenceType(), e.getDonationId(),
                e.isAiAssisted(), e.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("장부 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
