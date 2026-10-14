package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabElderDocumentDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabElderDocument;
import com.silverithm.vehicleplacementsystem.lab.repository.LabElderDocumentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수급자 서류함 — 서류의 종류·발급일·만료일·보관 위치·확인일을 어르신별로 기록한다(파일 자체는 담지 않는다).
 *
 * <p>어르신 ID는 클라이언트가 보내는 값이라 모든 저장·조회가 같은 기관 소속인지 확인한다(아니면 404).
 * 만료일은 발급일보다 빠를 수 없다. 갖춤 현황·보존 기한 계산은 웹이 한다.
 */
@Service
public class LabElderDocumentService {

    static final Set<String> DOC_TYPES = Set.of("CONTRACT", "RECOGNITION", "USE_PLAN", "NEEDS_ASSESSMENT",
            "CARE_PLAN", "PRIVACY_CONSENT", "RIGHTS_GUIDE", "RISK_ASSESSMENT", "OTHER");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabElderDocumentRepository documents;

    public LabElderDocumentService(LabAccessService access, LabElderDirectory elders,
                                   LabElderDocumentRepository documents) {
        this.access = access;
        this.elders = elders;
        this.documents = documents;
    }

    /** 어르신을 고르면 그 어르신의 서류, 아니면 기관 전체 */
    @Transactional(readOnly = true)
    public List<LabElderDocumentDto.View> list(Long elderlyId) {
        Long companyId = access.requireCompanyId();
        List<LabElderDocument> rows;
        if (elderlyId != null) {
            elders.requireInCompany(elderlyId, companyId);
            rows = documents.findByCompanyIdAndElderlyIdOrderByDocTypeAscIdAsc(companyId, elderlyId);
        } else {
            rows = documents.findByCompanyIdOrderByElderlyIdAscDocTypeAscIdAsc(companyId);
        }
        Map<Long, String> names = elders.namesInCompany(companyId);
        return rows.stream().map(d -> toView(d, names.getOrDefault(d.getElderlyId(), ""))).toList();
    }

    @Transactional
    public LabElderDocumentDto.View create(LabElderDocumentDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabElderDocument doc = new LabElderDocument(companyId);
        apply(doc, input, companyId);
        return toView(documents.saveAndFlush(doc), elders.nameOf(doc.getElderlyId()));
    }

    @Transactional
    public LabElderDocumentDto.View update(Long id, LabElderDocumentDto.Input input) {
        Long companyId = access.requireCompanyId();
        LabElderDocument doc = documents.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabElderDocumentService::notFound);
        apply(doc, input, companyId);
        return toView(documents.saveAndFlush(doc), elders.nameOf(doc.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        documents.delete(documents.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabElderDocumentService::notFound));
    }

    private void apply(LabElderDocument doc, LabElderDocumentDto.Input input, Long companyId) {
        if (input == null) {
            throw LabValidation.bad("서류 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);
        String docType = LabValidation.oneOf(input.docType(), DOC_TYPES, "서류 종류");
        String title = LabValidation.requiredText(input.title(), 100, "서류 제목");
        LocalDate issuedOn = optionalDate(input.issuedOn(), "발급일");
        LocalDate expiresOn = optionalDate(input.expiresOn(), "만료일");
        if (issuedOn != null && expiresOn != null && expiresOn.isBefore(issuedOn)) {
            throw LabValidation.bad("만료일은 발급일보다 빠를 수 없습니다");
        }
        String location = LabValidation.text(input.location(), 100, "보관 위치");
        LocalDate checkedOn = optionalDate(input.checkedOn(), "확인일");
        String note = LabValidation.text(input.note(), 500, "메모");
        doc.fill(input.elderlyId(), docType, title, issuedOn, expiresOn, location, checkedOn, note);
    }

    private static LocalDate optionalDate(LocalDate value, String label) {
        return value == null ? null : LabValidation.plausibleDate(value, label);
    }

    private static LabElderDocumentDto.View toView(LabElderDocument d, String elderlyName) {
        return new LabElderDocumentDto.View(d.getId(), d.getElderlyId(), elderlyName, d.getDocType(), d.getTitle(),
                d.getIssuedOn(), d.getExpiresOn(), d.getLocation(), d.getCheckedOn(), d.getNote(), d.getUpdatedAt());
    }

    private static CustomException notFound() {
        return new CustomException("서류를 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
