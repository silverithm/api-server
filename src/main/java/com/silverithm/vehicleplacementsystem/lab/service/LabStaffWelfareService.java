package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffWelfareDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabBonusFund;
import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffWelfare;
import com.silverithm.vehicleplacementsystem.lab.repository.LabBonusFundRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabLedgerEntryRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffWelfareRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원 복지(포상) 제공대장과 평가 가산금 수령·사용 — 직원·증빙(회계 장부 기록) id는 모두 요청자 기관 것인지 확인하고
 * 아니면 존재 여부를 알려 주지 않도록 404로 거절한다. 날짜는 오늘 이후일 수 없다.
 * 대표자 본인 수령(recipientIsOwner)·대표자 겸 직원 사용(ownerUse)은 그대로 저장하고, 인정 여부와 비율 계산은 웹이 한다.
 */
@Service
public class LabStaffWelfareService {

    /** 인정·불인정 종류 모두 — 불인정 종류(DINNER 등)도 기록은 남기고 판정은 웹이 한다 */
    static final Set<String> WELFARE_KINDS = Set.of("GIFT_CARD", "BOOKS", "TRAINING", "CLUB", "MEDICAL",
            "VACATION_PAY", "TRANSPORT", "CLOTHING", "VACCINE", "INSURANCE", "REWARD_LEAVE", "BONUS", "HOLIDAY_GIFT",
            "SPECIAL_ALLOWANCE", "OTHER", "DINNER", "PLAQUE_ONLY", "DONATED_GOODS");
    static final Set<String> FUND_KINDS = Set.of("RECEIVED", "SPENT");
    private static final long MAX_WELFARE = 100_000_000L;
    private static final long MAX_FUND = 1_000_000_000L;

    private final LabAccessService access;
    private final LabStaffService staff;
    private final LabStaffWelfareRepository welfare;
    private final LabBonusFundRepository funds;
    private final LabLedgerEntryRepository ledger;

    public LabStaffWelfareService(LabAccessService access, LabStaffService staff, LabStaffWelfareRepository welfare,
                                  LabBonusFundRepository funds, LabLedgerEntryRepository ledger) {
        this.access = access;
        this.staff = staff;
        this.welfare = welfare;
        this.funds = funds;
        this.ledger = ledger;
    }

    // ---- 복지 제공대장 ----

    @Transactional(readOnly = true)
    public List<LabStaffWelfareDto.WelfareView> listWelfare(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabStaffWelfare> rows = welfare.findByCompanyIdAndGivenOnBetweenOrderByGivenOnDescIdDesc(companyId,
                LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        Map<Long, String> names = staff.namesOf(rows.stream().map(LabStaffWelfare::getMemberId).distinct().toList(),
                companyId);
        return rows.stream().map(w -> view(w, names)).toList();
    }

    @Transactional
    public LabStaffWelfareDto.WelfareView createWelfare(LabStaffWelfareDto.WelfareInput input) {
        Long companyId = access.requireCompanyId();
        LabStaffWelfare row = new LabStaffWelfare(companyId);
        applyWelfare(row, input, companyId);
        return viewOf(welfare.saveAndFlush(row), companyId);
    }

    @Transactional
    public LabStaffWelfareDto.WelfareView updateWelfare(Long id, LabStaffWelfareDto.WelfareInput input) {
        Long companyId = access.requireCompanyId();
        LabStaffWelfare row = welfare.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> notFound("복지 제공 기록"));
        applyWelfare(row, input, companyId);
        return viewOf(welfare.saveAndFlush(row), companyId);
    }

    @Transactional
    public void deleteWelfare(Long id) {
        Long companyId = access.requireCompanyId();
        welfare.delete(welfare.findByIdAndCompanyId(id, companyId).orElseThrow(() -> notFound("복지 제공 기록")));
    }

    private void applyWelfare(LabStaffWelfare row, LabStaffWelfareDto.WelfareInput input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("복지 제공 내용을 보내 주세요");
        }
        LocalDate givenOn = LabValidation.pastOrToday(input.givenOn(), "제공일");
        if (input.memberId() == null) {
            throw LabValidation.bad("받은 직원을 선택해 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), WELFARE_KINDS, "복지 종류");
        String item = LabValidation.requiredText(input.item(), 100, "포상품·내용");
        long amount = LabValidation.longInRange(input.amount() == null ? Long.valueOf(0) : input.amount(), 0,
                MAX_WELFARE, "금액");
        String evidenceNote = LabValidation.text(input.evidenceNote(), 200, "증빙 보관 위치");
        String note = LabValidation.text(input.note(), 200, "메모");

        // 입력 모양이 맞은 뒤에 기관 소속을 확인한다
        staff.requireAllInCompany(List.of(input.memberId()), companyId);
        requireLedger(input.evidenceLedgerId(), companyId);

        row.fill(givenOn, input.memberId(), kind, item, amount, Boolean.TRUE.equals(input.signed()),
                Boolean.TRUE.equals(input.recipientIsOwner()), input.evidenceLedgerId(), evidenceNote, note);
    }

    private LabStaffWelfareDto.WelfareView viewOf(LabStaffWelfare w, Long companyId) {
        return view(w, staff.namesOf(List.of(w.getMemberId()), companyId));
    }

    private static LabStaffWelfareDto.WelfareView view(LabStaffWelfare w, Map<Long, String> names) {
        return new LabStaffWelfareDto.WelfareView(w.getId(), w.getGivenOn(), w.getMemberId(),
                names.getOrDefault(w.getMemberId(), ""), w.getKind(), w.getItem(), w.getAmount(), w.isSigned(),
                w.isRecipientIsOwner(), w.getEvidenceLedgerId(), w.getEvidenceNote(), w.getNote(),
                w.getUpdatedAt());
    }

    // ---- 가산금 ----

    @Transactional(readOnly = true)
    public List<LabStaffWelfareDto.BonusView> listFunds(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabBonusFund> rows = funds.findByCompanyIdAndOccurredOnBetweenOrderByOccurredOnDescIdDesc(companyId,
                LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        Set<Long> ids = new HashSet<>();
        rows.stream().map(LabBonusFund::getMemberId).filter(m -> m != null).forEach(ids::add);
        Map<Long, String> names = staff.namesOf(ids, companyId);
        return rows.stream().map(f -> fundView(f, names)).toList();
    }

    @Transactional
    public LabStaffWelfareDto.BonusView createFund(LabStaffWelfareDto.BonusInput input) {
        Long companyId = access.requireCompanyId();
        LabBonusFund row = new LabBonusFund(companyId);
        applyFund(row, input, companyId);
        return fundViewOf(funds.saveAndFlush(row), companyId);
    }

    @Transactional
    public LabStaffWelfareDto.BonusView updateFund(Long id, LabStaffWelfareDto.BonusInput input) {
        Long companyId = access.requireCompanyId();
        LabBonusFund row = funds.findByIdAndCompanyId(id, companyId).orElseThrow(() -> notFound("가산금 기록"));
        applyFund(row, input, companyId);
        return fundViewOf(funds.saveAndFlush(row), companyId);
    }

    @Transactional
    public void deleteFund(Long id) {
        Long companyId = access.requireCompanyId();
        funds.delete(funds.findByIdAndCompanyId(id, companyId).orElseThrow(() -> notFound("가산금 기록")));
    }

    private void applyFund(LabBonusFund row, LabStaffWelfareDto.BonusInput input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("가산금 내용을 보내 주세요");
        }
        String kind = LabValidation.oneOf(input.kind(), FUND_KINDS, "구분");
        LocalDate occurredOn = LabValidation.pastOrToday(input.occurredOn(), "일자");
        long amount = LabValidation.longInRange(input.amount(), 1, MAX_FUND, "금액");
        String purpose = LabValidation.requiredText(input.purpose(), 100, "수령 내용·사용 목적");
        String note = LabValidation.text(input.note(), 200, "메모");
        boolean spent = "SPENT".equals(kind);
        // 받은 돈에는 대상 직원·대표자 사용 표시를 둘 수 없다 — 사용(SPENT)에서만 의미가 있다
        if (!spent && input.memberId() != null) {
            throw LabValidation.bad("받은 가산금에는 직원을 지정하지 않습니다. 사용한 내역에서만 직원을 선택해 주세요");
        }
        if (!spent && Boolean.TRUE.equals(input.ownerUse())) {
            throw LabValidation.bad("대표자 사용 표시는 사용한 내역에서만 할 수 있습니다");
        }

        if (input.memberId() != null) {
            staff.requireAllInCompany(List.of(input.memberId()), companyId);
        }
        requireLedger(input.evidenceLedgerId(), companyId);

        row.fill(kind, occurredOn, amount, input.memberId(), purpose, Boolean.TRUE.equals(input.ownerUse()),
                input.evidenceLedgerId(), note);
    }

    private LabStaffWelfareDto.BonusView fundViewOf(LabBonusFund f, Long companyId) {
        return fundView(f, f.getMemberId() == null ? Map.of() : staff.namesOf(List.of(f.getMemberId()), companyId));
    }

    private static LabStaffWelfareDto.BonusView fundView(LabBonusFund f, Map<Long, String> names) {
        return new LabStaffWelfareDto.BonusView(f.getId(), f.getKind(), f.getOccurredOn(), f.getAmount(),
                f.getMemberId(), f.getMemberId() == null ? null : names.getOrDefault(f.getMemberId(), ""),
                f.getPurpose(), f.isOwnerUse(), f.getEvidenceLedgerId(), f.getNote(), f.getUpdatedAt());
    }

    /** 증빙 장부 기록이 있으면 요청자 기관 것이어야 한다 — 아니면 404 */
    private void requireLedger(Long ledgerId, Long companyId) {
        if (ledgerId != null && ledger.findByIdAndCompanyId(ledgerId, companyId).isEmpty()) {
            throw new CustomException("장부 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
        }
    }

    private static CustomException notFound(String what) {
        return new CustomException(what + "을(를) 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
