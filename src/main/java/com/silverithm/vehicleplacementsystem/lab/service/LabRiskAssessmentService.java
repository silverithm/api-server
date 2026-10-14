package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRiskDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabRiskAssessment;
import com.silverithm.vehicleplacementsystem.lab.repository.LabRiskAssessmentRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 욕창(Braden)·낙상(Morse) 위험도 평가.
 *
 * <p>합계와 위험 단계는 항상 서버가 항목 점수에서 계산한다({@link LabRiskScoring}). 평가는 쌓아서 이력으로
 * 남기고, 반기 점검 현황은 어르신별 최신 평가를 보고 웹이 판단한다.
 */
@Service
public class LabRiskAssessmentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final LabAccessService access;
    private final LabElderDirectory elders;
    private final LabRiskAssessmentRepository assessments;

    public LabRiskAssessmentService(LabAccessService access, LabElderDirectory elders,
                                    LabRiskAssessmentRepository assessments) {
        this.access = access;
        this.elders = elders;
        this.assessments = assessments;
    }

    /** 어르신 1명의 평가 이력, 최신순 */
    @Transactional(readOnly = true)
    public List<LabRiskDto.View> listByElder(Long elderlyId) {
        Long companyId = access.requireCompanyId();
        elders.requireInCompany(elderlyId, companyId);
        String name = elders.nameOf(elderlyId);
        return assessments.findByCompanyIdAndElderlyIdOrderByAssessedOnDescIdDesc(companyId, elderlyId).stream()
                .map(a -> toView(a, name))
                .toList();
    }

    /**
     * 기관의 모든 어르신 — 한 번도 평가하지 않은 분도 braden·morse를 null로 담아 포함한다.
     * 반기 점검에서는 "아직 안 한 분"이 가장 중요한 정보라 평가가 있는 어르신만 돌려주면 안 된다.
     */
    @Transactional(readOnly = true)
    public List<LabRiskDto.Latest> latest() {
        Long companyId = access.requireCompanyId();
        Map<Long, String> names = elders.namesInCompany(companyId);

        // 어르신·종류별로 같은 날 여러 건이면 나중에 쌓은(id 큰) 평가를 최신으로 본다
        Map<Long, LabRiskAssessment> braden = new HashMap<>();
        Map<Long, LabRiskAssessment> morse = new HashMap<>();
        for (LabRiskAssessment a : assessments.findLatestPerElderAndKind(companyId)) {
            Map<Long, LabRiskAssessment> target = LabRiskScoring.BRADEN.equals(a.getKind()) ? braden : morse;
            target.merge(a.getElderlyId(), a, (x, y) -> x.getId() >= y.getId() ? x : y);
        }

        return names.entrySet().stream()
                .map(e -> new LabRiskDto.Latest(e.getKey(), e.getValue(),
                        summary(braden.get(e.getKey())), summary(morse.get(e.getKey()))))
                .sorted(Comparator.comparing(LabRiskDto.Latest::elderlyName))
                .toList();
    }

    @Transactional
    public LabRiskDto.View create(LabRiskDto.Input input) {
        Long companyId = access.requireCompanyId();
        if (input == null) {
            throw LabValidation.bad("평가 내용을 보내 주세요");
        }
        elders.requireInCompany(input.elderlyId(), companyId);

        String kind = input.kind() == null ? "" : input.kind().strip().toUpperCase();
        if (!LabRiskScoring.isKind(kind)) {
            throw LabValidation.bad("평가 종류는 BRADEN 또는 MORSE여야 합니다");
        }
        LabRiskScoring.Result result = LabRiskScoring.score(kind, input.scores());

        LocalDate assessedOn = LabValidation.required(input.assessedOn(), "평가일");
        if (assessedOn.isAfter(LocalDate.now(SEOUL))) {
            throw LabValidation.bad("평가일은 오늘 이후일 수 없습니다");
        }
        String assessor = LabValidation.text(input.assessorName(), 50, "평가자 이름");
        String note = LabValidation.text(input.note(), 2000, "메모");

        LabRiskAssessment saved = assessments.save(new LabRiskAssessment(companyId, input.elderlyId(), kind,
                LabJson.write(result.scores()), result.total(), result.riskLevel(), assessedOn, assessor, note,
                Boolean.TRUE.equals(input.aiAssisted())));
        return toView(saved, elders.nameOf(saved.getElderlyId()));
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        LabRiskAssessment assessment = assessments.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomException("평가를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        assessments.delete(assessment);
    }

    private static LabRiskDto.Summary summary(LabRiskAssessment a) {
        return a == null ? null : new LabRiskDto.Summary(a.getAssessedOn(), a.getTotal(), a.getRiskLevel());
    }

    private static LabRiskDto.View toView(LabRiskAssessment a, String elderlyName) {
        return new LabRiskDto.View(a.getId(), a.getElderlyId(), elderlyName, a.getKind(),
                LabJson.readScores(a.getScores()), a.getTotal(), a.getRiskLevel(), a.getAssessedOn(),
                a.getAssessorName(), a.getNote(), a.isAiAssisted(), a.getCreatedAt());
    }
}
