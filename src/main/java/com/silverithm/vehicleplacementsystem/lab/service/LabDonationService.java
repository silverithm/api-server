package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabDonationDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabDonation;
import com.silverithm.vehicleplacementsystem.lab.repository.LabDonationRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabLedgerEntryRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후원금 — 접수·수정·삭제와 연도별 목록.
 *
 * <p>후원자 이름은 암호화 열(평문 50자 이하)이다. usedAmount는 입력을 받지 않고 연결된 지출(EXPENSE) 합으로
 * 서버가 계산하며, 목록은 후원금마다 묻지 않고 한 번의 묶음 쿼리로 구한다. 연결된 지출이 있는 후원금은 삭제를
 * 409로 막는다(후원금 행을 잠그고 검사해 그 사이 새 연결이 끼어들지 못한다). 영수증 번호는 기관 안에서 유일하다.
 */
@Service
public class LabDonationService {

    private static final Set<String> DONOR_TYPES = Set.of("INDIVIDUAL", "CORPORATE", "GROUP");
    private static final Set<String> KINDS = Set.of("CASH", "GOODS");
    private static final String DUPLICATE_RECEIPT = "이미 사용 중인 영수증 번호입니다";

    private final LabAccessService access;
    private final LabDonationRepository donations;
    private final LabLedgerEntryRepository ledger;

    public LabDonationService(LabAccessService access, LabDonationRepository donations,
                              LabLedgerEntryRepository ledger) {
        this.access = access;
        this.donations = donations;
        this.ledger = ledger;
    }

    /** 한 해치 — 받은 날 오름차순, 같은 날은 id 순. 사용액은 연결된 모든 지출(날짜 무관)의 합 */
    @Transactional(readOnly = true)
    public List<LabDonationDto.View> listByYear(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabDonation> rows = donations.findByCompanyIdAndReceivedOnBetweenOrderByReceivedOnAscIdAsc(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        Map<Long, Long> used = usedOf(rows.stream().map(LabDonation::getId).toList(), companyId);
        return rows.stream().map(d -> toView(d, used.getOrDefault(d.getId(), 0L))).toList();
    }

    @Transactional
    public LabDonationDto.View create(LabDonationDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabDonation donation = new LabDonation(companyId);
        apply(donation, input, companyId);
        return save(donation, companyId);
    }

    @Transactional
    public LabDonationDto.View update(Long id, LabDonationDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabDonation donation = donations.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabDonationService::notFound);
        apply(donation, input, companyId);
        return save(donation, companyId);
    }

    /** 연결된 지출이 있으면 409 */
    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabDonation donation = donations.findForUpdate(id, companyId).orElseThrow(LabDonationService::notFound);
        if (ledger.existsByCompanyIdAndDonationIdAndDirection(companyId, id, "EXPENSE")) {
            throw new CustomException("연결된 지출이 있는 후원금은 지울 수 없습니다. 지출의 연결을 먼저 풀어 주세요",
                    HttpStatus.CONFLICT);
        }
        donations.delete(donation);
    }

    private LabDonationDto.View save(LabDonation donation, Long companyId) {
        LabDonation saved;
        try {
            saved = donations.saveAndFlush(donation);
        } catch (DataIntegrityViolationException e) {
            // 같은 번호를 동시에 접수한 경우 — 유일 키가 마지막 방어선이다
            throw new CustomException(DUPLICATE_RECEIPT, HttpStatus.CONFLICT);
        }
        return toView(saved, usedOf(List.of(saved.getId()), companyId).getOrDefault(saved.getId(), 0L));
    }

    private void apply(LabDonation donation, LabDonationDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("후원금 내용을 보내 주세요");
        }
        LocalDate receivedOn = LabValidation.plausibleDate(input.receivedOn(), "받은 날");
        String donor = LabValidation.requiredText(input.donorName(), 50, "후원자 이름");
        String donorType = LabValidation.oneOf(input.donorType(), DONOR_TYPES, "후원자 구분");
        String kind = LabValidation.oneOf(input.kind(), KINDS, "후원 종류");
        long amount = LabValidation.longInRange(input.amount(), 1, LabLedgerService.MAX_AMOUNT, "금액");
        // 현금에는 물품 설명이 없다 — 보내도 저장하지 않는다
        String goods = "GOODS".equals(kind) ? LabValidation.text(input.goodsDescription(), 200, "물품 설명") : null;
        String purpose = LabValidation.text(input.designatedPurpose(), 200, "지정 용도");
        String receiptNo = LabValidation.text(input.receiptNo(), 30, "영수증 번호");
        if (receiptNo != null) {
            boolean taken = donations.findByReceiptNo(companyId, receiptNo).stream()
                    .anyMatch(other -> !other.getId().equals(donation.getId()));
            if (taken) {
                throw new CustomException(DUPLICATE_RECEIPT, HttpStatus.CONFLICT);
            }
        }
        donation.fill(receivedOn, donor, donorType, kind, amount, goods, purpose, receiptNo);
    }

    /** 후원금 id별 연결된 지출 합 — 한 번의 쿼리 */
    private Map<Long, Long> usedOf(List<Long> ids, Long companyId) {
        Map<Long, Long> used = new HashMap<>();
        if (ids.isEmpty()) {
            return used;
        }
        for (LabLedgerEntryRepository.UsedRow row : ledger.sumExpensesByDonationIds(companyId, ids)) {
            used.put(row.getDonationId(), row.getTotal() == null ? 0L : row.getTotal());
        }
        return used;
    }

    private static LabDonationDto.View toView(LabDonation d, long used) {
        return new LabDonationDto.View(d.getId(), d.getReceivedOn(), d.getDonorName(), d.getDonorType(), d.getKind(),
                d.getAmount(), d.getGoodsDescription(), d.getDesignatedPurpose(), d.getReceiptNo(), used,
                d.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("후원금을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
