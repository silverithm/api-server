package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabResourceLinkDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabResourceLink;
import com.silverithm.vehicleplacementsystem.lab.repository.LabResourceLinkRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부기관·지역자원 연계 — 등록·수정·삭제와 연도별/어르신별 조회(최신순).
 *
 * <p>elderlyId가 null이면 어르신 한 명이 아니라 기관 전체의 연계다. 있으면 같은 기관 어르신이어야 한다(아니면 404).
 * 담당자 이름·연계 내용·결과는 암호화되어 저장된다.
 */
@Service
public class LabResourceLinkService {

    private static final int LONG_TEXT = 2000;
    private static final int OUTCOME = 1000;
    private static final int INSTITUTION = 100;
    private static final int NAME = 50;
    private static final Set<String> TYPES = Set.of("MEDICAL", "HEALTH_CENTER", "DEMENTIA_CENTER", "WELFARE",
            "COMMUNITY", "GOODS", "EDUCATION", "OTHER");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabResourceLinkRepository links;

    public LabResourceLinkService(LabAccessService access, LabElderDirectory elders,
                                  LabResourceLinkRepository links) {
        this.access = access;
        this.elders = elders;
        this.links = links;
    }

    /** year 또는 elderlyId 중 하나는 필요하다(둘 다면 그 어르신의 그 해). 최신순 */
    @Transactional(readOnly = true)
    public List<LabResourceLinkDto.View> list(String year, Long elderlyId) {
        Long companyId = access.requireCompanyId();
        if (year == null && elderlyId == null) {
            throw LabValidation.bad("연도(YYYY) 또는 어르신을 지정해 주세요");
        }
        LocalDate from = null;
        LocalDate to = null;
        if (year != null) {
            int y = LabValidation.year(year, "연도");
            from = LocalDate.of(y, 1, 1);
            to = LocalDate.of(y, 12, 31);
        }
        List<LabResourceLink> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = from == null
                    ? links.findByCompanyIdAndElderlyIdOrderByLinkedOnDescIdDesc(companyId, elderlyId)
                    : links.findByCompanyIdAndElderlyIdAndLinkedOnBetweenOrderByLinkedOnDescIdDesc(
                            companyId, elderlyId, from, to);
        } else {
            rows = links.findByCompanyIdAndLinkedOnBetweenOrderByLinkedOnDescIdDesc(companyId, from, to);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(l -> toView(l, l.getElderlyId() == null ? null
                : names.getOrDefault(l.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabResourceLinkDto.View create(LabResourceLinkDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabResourceLink link = new LabResourceLink(companyId);
        apply(link, input, companyId);
        return view(links.saveAndFlush(link));
    }

    @Transactional
    public LabResourceLinkDto.View update(Long id, LabResourceLinkDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabResourceLink link = links.findByIdAndCompanyId(id, companyId).orElseThrow(LabResourceLinkService::notFound);
        apply(link, input, companyId);
        return view(links.saveAndFlush(link));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        links.delete(links.findByIdAndCompanyId(id, companyId).orElseThrow(LabResourceLinkService::notFound));
    }

    private void apply(LabResourceLink link, LabResourceLinkDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("연계 내용을 보내 주세요");
        }
        if (input.elderlyId() != null) {
            elders.requireInCompany(input.elderlyId(), companyId);
        }
        LocalDate linkedOn = LabValidation.plausibleDate(input.linkedOn(), "연계일");
        String type = LabValidation.oneOf(input.resourceType(), TYPES, "연계 종류");
        String institution = LabValidation.requiredText(input.institution(), INSTITUTION, "기관 이름");
        String contact = LabValidation.text(input.contactPerson(), NAME, "담당자 이름");
        String content = LabValidation.requiredText(input.content(), LONG_TEXT, "연계 내용");
        String outcome = LabValidation.text(input.outcome(), OUTCOME, "연계 결과");
        LocalDate followUpOn = input.followUpOn() == null ? null
                : LabValidation.plausibleDate(input.followUpOn(), "후속 확인 예정일");
        String writer = LabValidation.text(input.writerName(), NAME, "작성자 이름");
        link.fill(input.elderlyId(), linkedOn, type, institution, contact, content, outcome, followUpOn,
                Boolean.TRUE.equals(input.referralGiven()), writer, Boolean.TRUE.equals(input.aiAssisted()));
    }

    private LabResourceLinkDto.View view(LabResourceLink saved) {
        return toView(saved, saved.getElderlyId() == null ? null : elders.nameOf(saved.getElderlyId()));
    }

    private static LabResourceLinkDto.View toView(LabResourceLink l, String elderlyName) {
        return new LabResourceLinkDto.View(l.getId(), l.getElderlyId(), elderlyName, l.getLinkedOn(),
                l.getResourceType(), l.getInstitution(), l.getContactPerson(), l.getContent(), l.getOutcome(),
                l.getFollowUpOn(), l.isReferralGiven(), l.getWriterName(), l.isAiAssisted(), l.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("연계 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
