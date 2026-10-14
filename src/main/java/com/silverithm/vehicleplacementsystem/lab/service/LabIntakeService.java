package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabIntakeDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabIntakeInquiry;
import com.silverithm.vehicleplacementsystem.lab.repository.LabIntakeInquiryRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용 문의·입소 상담·대기.
 *
 * <p>연락처(전화번호)를 저장하려면 개인정보 수집·이용 동의일이 있어야 한다. 입소를 거절(DECLINED)할 때는 사유가
 * 필수다(노인장기요양보험법 제35조: 정당한 사유 없는 거부 금지) — 그 밖의 상태에서는 거절 사유·메모를 비운다.
 * 계약 완료(CONTRACTED)는 어르신으로 등록된 뒤라 elderly_id(같은 기관)가 필수다.
 * 신청 어르신·문의자 이름, 연락처, 욕구는 암호화한다.
 */
@Service
public class LabIntakeService {

    static final String CONSENT_REQUIRED = "개인정보 수집·이용 동의일을 적어야 연락처를 저장할 수 있습니다";
    static final Set<String> STATUSES = Set.of("NEW", "CONSULTED", "WAITING", LabIntakeInquiry.CONTRACTED,
            LabIntakeInquiry.DECLINED, "WITHDRAWN");
    private static final Set<String> SOURCES = Set.of("PHONE", "VISIT", "REFERRAL", "WEB", "OTHER");
    private static final Set<String> RELATIONS = Set.of("SELF", "SPOUSE", "CHILD", "OTHER_FAMILY", "OTHER");
    private static final Set<String> GRADES = Set.of("1", "2", "3", "4", "5", "cognitive", "NONE", "APPLYING");
    private static final Set<String> DECLINE_REASONS = Set.of("CAPACITY", "OUT_OF_AREA", "SERVICE_MISMATCH", "OTHER");
    private static final int NAME = 50;

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabIntakeInquiryRepository inquiries;

    public LabIntakeService(LabAccessService access, LabElderDirectory elders,
                            LabIntakeInquiryRepository inquiries) {
        this.access = access;
        this.elders = elders;
        this.inquiries = inquiries;
    }

    /** status로 좁힌다(없으면 전부). 문의일 최신순 */
    @Transactional(readOnly = true)
    public List<LabIntakeDto.View> list(String status) {
        Long companyId = access.requireCompanyId();
        String wanted = status == null || status.isBlank() ? null : LabValidation.oneOf(status, STATUSES, "상태");
        List<LabIntakeInquiry> rows = wanted == null
                ? inquiries.findByCompanyIdOrderByInquiryDateDescIdDesc(companyId)
                : inquiries.findByCompanyIdAndStatusOrderByInquiryDateDescIdDesc(companyId, wanted);
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(i -> toView(i, i.getElderlyId() == null ? null
                : names.getOrDefault(i.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabIntakeDto.View create(LabIntakeDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabIntakeInquiry inquiry = new LabIntakeInquiry(companyId);
        apply(inquiry, input, companyId);
        return save(inquiry);
    }

    @Transactional
    public LabIntakeDto.View update(Long id, LabIntakeDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabIntakeInquiry inquiry = inquiries.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabIntakeService::notFound);
        apply(inquiry, input, companyId);
        return save(inquiry);
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        inquiries.delete(inquiries.findByIdAndCompanyId(id, companyId).orElseThrow(LabIntakeService::notFound));
    }

    private LabIntakeDto.View save(LabIntakeInquiry inquiry) {
        LabIntakeInquiry saved = inquiries.saveAndFlush(inquiry);
        return toView(saved, saved.getElderlyId() == null ? null : elders.nameOf(saved.getElderlyId()));
    }

    private void apply(LabIntakeInquiry inquiry, LabIntakeDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("상담 내용을 보내 주세요");
        }
        LocalDate inquiryDate = LabValidation.plausibleDate(input.inquiryDate(), "문의일");
        String applicant = LabValidation.requiredText(input.applicantName(), NAME, "신청 어르신 이름");
        String contactName = LabValidation.text(input.contactName(), NAME, "문의자 이름");
        String contactPhone = LabValidation.text(input.contactPhone(), 20, "연락처");
        LocalDate consentOn = input.consentOn() == null ? null
                : LabValidation.plausibleDate(input.consentOn(), "개인정보 동의일");
        if (contactPhone != null && consentOn == null) {
            throw LabValidation.bad(CONSENT_REQUIRED);
        }
        String relation = optional(input.relation(), RELATIONS, "문의자 관계");
        String careGrade = optional(input.careGrade(), GRADES, "장기요양등급");
        String desiredDays = LabValidation.text(input.desiredDays(), 60, "희망 요일");
        String needs = LabValidation.text(input.needs(), 2000, "욕구");
        String source = LabValidation.oneOf(input.source(), SOURCES, "문의 경로");
        String status = LabValidation.oneOf(input.status(), STATUSES, "상태");

        String declineReason = null;
        String declineNote = null;
        if (LabIntakeInquiry.DECLINED.equals(status)) {
            if (input.declineReason() == null || input.declineReason().isBlank()) {
                throw LabValidation.bad("거절 사유를 골라 주세요(정당한 사유 없이 입소를 거부할 수 없습니다)");
            }
            declineReason = LabValidation.oneOf(input.declineReason(), DECLINE_REASONS, "거절 사유");
            declineNote = LabValidation.text(input.declineNote(), 500, "거절 메모");
        }
        LocalDate nextContactOn = input.nextContactOn() == null ? null
                : LabValidation.plausibleDate(input.nextContactOn(), "다음 연락일");

        Long elderlyId = input.elderlyId();
        if (LabIntakeInquiry.CONTRACTED.equals(status) && elderlyId == null) {
            throw LabValidation.bad("계약을 마친 상담은 어르신을 연결해 주세요");
        }
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
        }
        String counselor = LabValidation.text(input.counselorName(), NAME, "상담자 이름");
        inquiry.fill(inquiryDate, applicant, contactName, contactPhone, relation, careGrade, desiredDays, needs,
                source, status, declineReason, declineNote, consentOn, nextContactOn, elderlyId, counselor,
                Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static String optional(String value, Set<String> allowed, String label) {
        return value == null || value.isBlank() ? null : LabValidation.oneOf(value, allowed, label);
    }

    private static LabIntakeDto.View toView(LabIntakeInquiry i, String elderlyName) {
        return new LabIntakeDto.View(i.getId(), i.getInquiryDate(), i.getApplicantName(), i.getContactName(),
                i.getContactPhone(), i.getRelation(), i.getCareGrade(), i.getDesiredDays(), i.getNeeds(),
                i.getSource(), i.getStatus(), i.getDeclineReason(), i.getDeclineNote(), i.getConsentOn(),
                i.getNextContactOn(), i.getElderlyId(), elderlyName, i.getCounselorName(), i.isAiAssisted(),
                i.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("입소 상담 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
