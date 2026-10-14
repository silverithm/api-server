package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabConsultationDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabConsultation;
import com.silverithm.vehicleplacementsystem.lab.repository.LabConsultationRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상담일지 — 어르신 본인·보호자 상담 기록. 등록·수정·삭제와 어르신별/월별 조회(최신순).
 *
 * <p>같은 날 같은 어르신을 여러 번 상담할 수 있어 upsert가 아니라 쌓는 기록이다. 상대 이름·상담 내용·후속
 * 조치는 암호화되어 저장된다. 어르신 소속은 등록·수정 때마다 서비스가 직접 확인한다.
 */
@Service
public class LabConsultationService {

    private static final int LONG_TEXT = 2000;
    private static final int NAME = 50;
    private static final Set<String> COUNTERPARTS = Set.of("SELF", "GUARDIAN", "FAMILY", "OTHER");
    private static final Set<String> METHODS = Set.of("VISIT", "PHONE", "ONLINE", "OTHER");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabConsultationRepository consultations;

    public LabConsultationService(LabAccessService access, LabElderDirectory elders,
                                  LabConsultationRepository consultations) {
        this.access = access;
        this.elders = elders;
        this.consultations = consultations;
    }

    /**
     * elderlyId만 주면 그 어르신의 전체 이력, month만 주면 기관 전체의 그 달, 둘 다 주면 그 어르신의 그 달.
     * 둘 다 없으면 400. 최신순(상담일 내림차순, 같은 날은 나중에 쓴 것이 먼저).
     */
    @Transactional(readOnly = true)
    public List<LabConsultationDto.View> list(Long elderlyId, String month) {
        Long companyId = access.requireCompanyId();
        if (elderlyId == null && month == null) {
            throw LabValidation.bad("어르신 또는 월(YYYY-MM)을 지정해 주세요");
        }
        YearMonth ym = month == null ? null : LabValidation.yearMonth(month, "월");

        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            String name = elders.nameOf(elderlyId);
            List<LabConsultation> rows = ym == null
                    ? consultations.findByCompanyIdAndElderlyIdOrderByConsultedOnDescIdDesc(companyId, elderlyId)
                    : consultations.findByCompanyIdAndElderlyIdAndConsultedOnBetweenOrderByConsultedOnDescIdDesc(
                            companyId, elderlyId, ym.atDay(1), ym.atEndOfMonth());
            return rows.stream().map(c -> toView(c, name)).toList();
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return consultations.findByCompanyIdAndConsultedOnBetweenOrderByConsultedOnDescIdDesc(
                        companyId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(c -> toView(c, names.getOrDefault(c.getElderlyId(), "")))
                .toList();
    }

    @Transactional
    public LabConsultationDto.View create(LabConsultationDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabConsultation consultation = new LabConsultation(companyId);
        apply(consultation, input, companyId);
        LabConsultation saved = consultations.saveAndFlush(consultation);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public LabConsultationDto.View update(Long id, LabConsultationDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabConsultation consultation = consultations.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabConsultationService::notFound);
        apply(consultation, input, companyId);
        LabConsultation saved = consultations.saveAndFlush(consultation);
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        consultations.delete(consultations.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabConsultationService::notFound));
    }

    private void apply(LabConsultation consultation, LabConsultationDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("상담 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        LocalDate day = LabValidation.required(input.consultedOn(), "상담일");
        String counterpart = LabValidation.oneOf(input.counterpart(), COUNTERPARTS, "상담 대상");
        String counterpartName = LabValidation.text(input.counterpartName(), NAME, "상담 상대 이름");
        String method = LabValidation.oneOf(input.method(), METHODS, "상담 방법");
        // 내용 없는 상담일지는 근거 기록이 못 되므로 필수로 둔다
        String content = LabValidation.requiredText(input.content(), LONG_TEXT, "상담 내용");
        String followUp = LabValidation.text(input.followUp(), LONG_TEXT, "후속 조치");
        String consultant = LabValidation.text(input.consultantName(), NAME, "상담자 이름");
        consultation.fill(input.elderlyId(), day, counterpart, counterpartName, method, content, followUp,
                consultant, Boolean.TRUE.equals(input.aiAssisted()));
    }

    private static LabConsultationDto.View toView(LabConsultation c, String elderlyName) {
        return new LabConsultationDto.View(c.getId(), c.getElderlyId(), elderlyName, c.getConsultedOn(),
                c.getCounterpart(), c.getCounterpartName(), c.getMethod(), c.getContent(), c.getFollowUp(),
                c.getConsultantName(), c.isAiAssisted(), c.getCreatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("상담일지를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
