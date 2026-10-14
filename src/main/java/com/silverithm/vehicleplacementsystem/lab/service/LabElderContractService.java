package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabElderContractDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabElderContract;
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
 * 급여 계약·장기요양 인정.
 *
 * <p>같은 어르신의 진행 중(ACTIVE) 계약은 하나뿐이다 — 새 계약을 받으려면 먼저 전 계약을 종료(ENDED)해야 한다.
 * 종료 계약은 종료일(계약 시작일 이후)과 사유가 필수이고, 진행 중 계약의 종료일·사유는 비운다(다시 진행으로 돌릴 때 옛 사유가
 * 남지 않게). 인정 유효기간은 끝이 시작보다 앞설 수 없다. 인정번호·메모는 암호화한다.
 */
@Service
public class LabElderContractService {

    private static final int NOTE = 1000;
    private static final int RECOGNITION_NO = 20;
    private static final int MAX_RENEWAL = 99;
    private static final Set<String> GRADES = Set.of("1", "2", "3", "4", "5", "cognitive");
    private static final Set<String> COPAY_CLASSES = Set.of("standard", "reduced40", "reduced60", "medicalAid");
    private static final Set<String> STATUSES = Set.of(LabElderContract.ACTIVE, LabElderContract.ENDED);
    private static final Set<String> END_REASONS = Set.of("TRANSFER", "HOSPITAL", "DEATH", "SELF", "OTHER");
    static final String SECOND_ACTIVE = "이 어르신의 진행 중인 계약이 이미 있습니다";

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabElderContractRepository contracts;

    public LabElderContractService(LabAccessService access, LabElderDirectory elders,
                                   LabElderContractRepository contracts) {
        this.access = access;
        this.elders = elders;
        this.contracts = contracts;
    }

    /** status / elderlyId로 좁힌다(둘 다 없으면 전부). 계약 시작일 최신순 */
    @Transactional(readOnly = true)
    public List<LabElderContractDto.View> list(String status, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        String wanted = status == null || status.isBlank() ? null : LabValidation.oneOf(status, STATUSES, "상태");
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }
        List<LabElderContract> rows;
        if (elderlyId != null && wanted != null) {
            rows = contracts.findByCompanyIdAndElderlyIdAndStatusOrderByContractStartDescIdDesc(companyId, elderlyId,
                    wanted);
        } else if (elderlyId != null) {
            rows = contracts.findByCompanyIdAndElderlyIdOrderByContractStartDescIdDesc(companyId, elderlyId);
        } else if (wanted != null) {
            rows = contracts.findByCompanyIdAndStatusOrderByContractStartDescIdDesc(companyId, wanted);
        } else {
            rows = contracts.findByCompanyIdOrderByContractStartDescIdDesc(companyId);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(c -> toView(c, names.getOrDefault(c.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabElderContractDto.View create(LabElderContractDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabElderContract contract = new LabElderContract(companyId);
        apply(contract, input, companyId);
        return save(contract);
    }

    @Transactional
    public LabElderContractDto.View update(Long id, LabElderContractDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabElderContract contract = contracts.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabElderContractService::notFound);
        apply(contract, input, companyId);
        return save(contract);
    }

    /** 퇴소 연계기록지는 계약 id가 NULL로 바뀌고 남는다(DB ON DELETE SET NULL) */
    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        contracts.delete(contracts.findByIdAndCompanyId(id, companyId).orElseThrow(LabElderContractService::notFound));
    }

    private LabElderContractDto.View save(LabElderContract contract) {
        try {
            LabElderContract saved = contracts.saveAndFlush(contract);
            return toView(saved, elders.nameOf(saved.getElderlyId()));
        } catch (DataIntegrityViolationException e) {
            // 서비스의 확인과 저장 사이에 다른 요청이 ACTIVE 계약을 넣은 경우 — DB의 가상 열 유일 키가 막은 것이다
            throw new CustomException(SECOND_ACTIVE, HttpStatus.CONFLICT);
        }
    }

    private void apply(LabElderContract contract, LabElderContractDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("계약 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate start = LabValidation.plausibleDate(input.contractStart(), "계약 시작일");
        LocalDate end = input.contractEnd() == null ? null : LabValidation.plausibleDate(input.contractEnd(), "계약 종료일");
        if (end != null && end.isBefore(start)) {
            throw LabValidation.bad("계약 종료일이 시작일보다 앞일 수 없습니다");
        }
        String grade = LabValidation.oneOf(input.careGrade(), GRADES, "장기요양등급");
        String recognitionNo = LabValidation.text(input.recognitionNo(), RECOGNITION_NO, "장기요양인정번호");
        LocalDate validFrom = LabValidation.plausibleDate(input.validFrom(), "인정 유효기간 시작일");
        LocalDate validTo = LabValidation.plausibleDate(input.validTo(), "인정 유효기간 끝나는 날");
        if (validTo.isBefore(validFrom)) {
            throw LabValidation.bad("인정 유효기간 끝나는 날이 시작일보다 앞일 수 없습니다");
        }
        long renewal = LabValidation.longInRange(input.renewalCount() == null ? Long.valueOf(0)
                : input.renewalCount().longValue(), 0, MAX_RENEWAL, "갱신 횟수");
        String copayClass = LabValidation.oneOf(input.copayClass(), COPAY_CLASSES, "본인부담 구분");
        Short planned = input.plannedDaysPerMonth() == null ? null
                : (short) LabValidation.longInRange(input.plannedDaysPerMonth().longValue(), 1, 31, "월 이용 계약 일수");
        LocalDate signedOn = date(input.signedOn(), "계약 서명일");
        LocalDate contractNotified = date(input.contractNotifiedOn(), "공단 계약통보일");
        LocalDate planNotified = date(input.planNotifiedOn(), "급여제공계획 통보일");
        LocalDate rightsGuided = date(input.rightsGuidedOn(), "노인인권 지침 안내일");
        String status = input.status() == null || input.status().isBlank()
                ? LabElderContract.ACTIVE : LabValidation.oneOf(input.status(), STATUSES, "상태");
        String endReason = null;
        if (LabElderContract.ENDED.equals(status)) {
            if (end == null) {
                throw LabValidation.bad("종료한 계약은 종료일을 적어 주세요");
            }
            if (input.endReason() == null || input.endReason().isBlank()) {
                throw LabValidation.bad("종료한 계약은 종료 사유를 골라 주세요");
            }
            endReason = LabValidation.oneOf(input.endReason(), END_REASONS, "종료 사유");
        } else {
            // 새 계약은 id가 없으니 어떤 id와도 겹치지 않는 -1로 "자기 자신 제외"를 건너뛴다
            if (contracts.existsByElderlyIdAndStatusAndIdNot(input.elderlyId(), LabElderContract.ACTIVE,
                    contract.getId() == null ? -1L : contract.getId())) {
                throw new CustomException(SECOND_ACTIVE, HttpStatus.CONFLICT);
            }
        }
        String note = LabValidation.text(input.note(), NOTE, "메모");
        contract.fill(input.elderlyId(), start, end, grade, recognitionNo, validFrom, validTo, (short) renewal,
                copayClass, planned, signedOn, Boolean.TRUE.equals(input.copyGiven()), contractNotified,
                planNotified, rightsGuided, status, endReason, note);
    }

    private static LocalDate date(LocalDate value, String label) {
        return value == null ? null : LabValidation.plausibleDate(value, label);
    }

    private static LabElderContractDto.View toView(LabElderContract c, String elderlyName) {
        return new LabElderContractDto.View(c.getId(), c.getElderlyId(), elderlyName, c.getContractStart(),
                c.getContractEnd(), c.getCareGrade(), c.getRecognitionNo(), c.getValidFrom(), c.getValidTo(),
                c.getRenewalCount(), c.getCopayClass(), c.getPlannedDaysPerMonth() == null ? null
                : c.getPlannedDaysPerMonth().intValue(), c.getSignedOn(), c.isCopyGiven(),
                c.getContractNotifiedOn(), c.getPlanNotifiedOn(), c.getRightsGuidedOn(), c.getStatus(),
                c.getEndReason(), c.getNote(), c.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("계약을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
