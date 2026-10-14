package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabDischargeReferralDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabDischargeReferral;
import com.silverithm.vehicleplacementsystem.lab.entity.LabElderContract;
import com.silverithm.vehicleplacementsystem.lab.repository.LabDischargeReferralRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabElderContractRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 퇴소 연계기록지(2026 평가 30 기준②)와 제공대장.
 *
 * <p>같은 어르신의 같은 퇴소일에는 한 장만 쓴다(다시 쓰면 409). 심신기능·제공 급여·퇴소 후 계획은 필수이고 암호화한다.
 * 제공대장 네 칸(제공일자·방법·제공자·수령자)은 함께 있거나 함께 없어야 한다 — 일부만 적힌 대장은 "줬는지"를 증명하지
 * 못하기 때문이다. 계약을 연결하면 같은 기관·같은 어르신의 계약이어야 한다.
 */
@Service
public class LabDischargeReferralService {

    private static final int NAME = 50;
    private static final int LONG_TEXT = 2000;
    private static final int PLAN_TEXT = 1000;
    private static final Set<String> REASONS = Set.of("TRANSFER", "HOSPITAL", "SELF", "OTHER");
    private static final Set<String> GRADES = Set.of("1", "2", "3", "4", "5", "cognitive");
    private static final Set<String> METHODS = Set.of("VISIT", "MAIL", "FAX", "ELECTRONIC", "OTHER");
    static final String PROVIDED_ALL = "제공대장 네 칸(제공일자·방법·제공자·수령자)을 함께 적어 주세요";
    static final String DUPLICATE = "이 어르신의 같은 퇴소일 연계기록지가 이미 있습니다";

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabDischargeReferralRepository referrals;
    private final LabElderContractRepository contracts;

    public LabDischargeReferralService(LabAccessService access, LabElderDirectory elders,
                                       LabDischargeReferralRepository referrals,
                                       LabElderContractRepository contracts) {
        this.access = access;
        this.elders = elders;
        this.referrals = referrals;
        this.contracts = contracts;
    }

    /** year(퇴소일이 그 해) / elderlyId로 좁힌다(둘 다 없으면 전부). 퇴소일 최신순 */
    @Transactional(readOnly = true)
    public List<LabDischargeReferralDto.View> list(String year, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        Integer y = year == null || year.isBlank() ? null : LabValidation.year(year, "연도");
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }
        List<LabDischargeReferral> rows;
        if (y != null && elderlyId != null) {
            rows = referrals.findByCompanyIdAndElderlyIdAndDischargeOnBetweenOrderByDischargeOnDescIdDesc(companyId,
                    elderlyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        } else if (y != null) {
            rows = referrals.findByCompanyIdAndDischargeOnBetweenOrderByDischargeOnDescIdDesc(companyId,
                    LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        } else if (elderlyId != null) {
            rows = referrals.findByCompanyIdAndElderlyIdOrderByDischargeOnDescIdDesc(companyId, elderlyId);
        } else {
            rows = referrals.findByCompanyIdOrderByDischargeOnDescIdDesc(companyId);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(r -> toView(r, names.getOrDefault(r.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabDischargeReferralDto.View create(LabDischargeReferralDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabDischargeReferral referral = new LabDischargeReferral(companyId);
        apply(referral, input, companyId);
        return save(referral);
    }

    @Transactional
    public LabDischargeReferralDto.View update(Long id, LabDischargeReferralDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabDischargeReferral referral = referrals.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabDischargeReferralService::notFound);
        apply(referral, input, companyId);
        return save(referral);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        referrals.delete(referrals.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabDischargeReferralService::notFound));
    }

    private LabDischargeReferralDto.View save(LabDischargeReferral referral) {
        try {
            LabDischargeReferral saved = referrals.saveAndFlush(referral);
            return toView(saved, elders.nameOf(saved.getElderlyId()));
        } catch (DataIntegrityViolationException e) {
            // 확인과 저장 사이에 같은 퇴소일 기록지가 들어온 경우 — 유일 키가 막은 것이다
            throw new CustomException(DUPLICATE, HttpStatus.CONFLICT);
        }
    }

    private void apply(LabDischargeReferral referral, LabDischargeReferralDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("연계기록지 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        Long contractId = null;
        if (input.contractId() != null) {
            LabElderContract contract = contracts.findByIdAndCompanyId(input.contractId(), companyId)
                    .orElseThrow(() -> new CustomException("계약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
            if (!contract.getElderlyId().equals(input.elderlyId())) {
                throw LabValidation.bad("다른 어르신의 계약은 연결할 수 없습니다");
            }
            contractId = contract.getId();
        }
        LocalDate dischargeOn = LabValidation.plausibleDate(input.dischargeOn(), "퇴소일");
        String reason = LabValidation.oneOf(input.reason(), REASONS, "퇴소 사유");
        LocalDate writtenOn = LabValidation.plausibleDate(input.writtenOn(), "작성일");
        String grade = LabValidation.oneOf(input.careGrade(), GRADES, "장기요양등급");
        String functional = LabValidation.requiredText(input.functionalStatus(), LONG_TEXT, "심신기능 상태");
        String services = LabValidation.requiredText(input.servicesProvided(), LONG_TEXT, "제공한 급여");
        String afterPlan = LabValidation.requiredText(input.afterPlan(), PLAN_TEXT, "퇴소 후 서비스 이용계획");
        String counsel = LabValidation.text(input.counselNote(), PLAN_TEXT, "종료 상담");

        String method = LabValidation.text(input.providedMethod(), 12, "제공 방법");
        String provider = LabValidation.text(input.providerName(), NAME, "제공자 이름");
        String recipient = LabValidation.text(input.recipientName(), NAME, "수령자 이름");
        LocalDate providedOn = input.providedOn() == null ? null : LabValidation.plausibleDate(input.providedOn(), "제공일자");
        int filled = (providedOn != null ? 1 : 0) + (method != null ? 1 : 0) + (provider != null ? 1 : 0)
                + (recipient != null ? 1 : 0);
        if (filled != 0 && filled != 4) {
            throw LabValidation.bad(PROVIDED_ALL);
        }
        if (method != null) {
            method = LabValidation.oneOf(method, METHODS, "제공 방법");
        }
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");

        // 같은 어르신·같은 퇴소일은 한 장 — 자기 자신(수정)은 빼고 본다
        if (referrals.existsByElderlyIdAndDischargeOnAndIdNot(input.elderlyId(), dischargeOn,
                referral.getId() == null ? -1L : referral.getId())) {
            throw new CustomException(DUPLICATE, HttpStatus.CONFLICT);
        }
        referral.fill(input.elderlyId(), contractId, dischargeOn, reason, writtenOn, grade, functional, services,
                afterPlan, counsel, providedOn, method, provider, recipient, writer,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static LabDischargeReferralDto.View toView(LabDischargeReferral r, String elderlyName) {
        return new LabDischargeReferralDto.View(r.getId(), r.getElderlyId(), elderlyName, r.getContractId(),
                r.getDischargeOn(), r.getReason(), r.getWrittenOn(), r.getCareGrade(), r.getFunctionalStatus(),
                r.getServicesProvided(), r.getAfterPlan(), r.getCounselNote(), r.getProvidedOn(),
                r.getProvidedMethod(), r.getProviderName(), r.getRecipientName(), r.getWriterName(),
                r.isAiAssisted(), r.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("연계기록지를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
