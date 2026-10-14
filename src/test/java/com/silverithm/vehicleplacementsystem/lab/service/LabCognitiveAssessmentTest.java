package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabRiskDto;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabCognitiveAssessmentTest {

    @Autowired private LabRiskAssessmentService service;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Elderly kim;
    private Elderly park;
    private static final LocalDate DAY = LocalDate.of(2026, 8, 1);

    @BeforeEach
    void setUp() {
        Company forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private static Map<String, Object> scores(Object tool, Object score, Object judgment, Object institution) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (tool != null) {
            m.put("tool", tool);
        }
        if (score != null) {
            m.put("score", score);
        }
        if (judgment != null) {
            m.put("judgment", judgment);
        }
        if (institution != null) {
            m.put("institution", institution);
        }
        return m;
    }

    private LabRiskDto.View save(Elderly elder, Map<String, ?> scores, LocalDate on) {
        return service.create(new LabRiskDto.Input(elder.getId(), "COGNITIVE", scores, on, "박간호", null, false));
    }

    private void assertBad(Map<String, ?> scores) {
        assertThatThrownBy(() -> save(kim, scores, DAY)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // 도구·점수·판정이 그대로 저장되고 total=점수, riskLevel=판정이 된다
    @Test
    void cognitiveStoresToolScoreAndJudgment() {
        LabRiskDto.View kmmse = save(kim, scores("KMMSE", 21, "SUSPECTED", null), DAY);
        assertThat(kmmse.kind()).isEqualTo("COGNITIVE");
        assertThat(kmmse.total()).isEqualTo(21);
        assertThat(kmmse.riskLevel()).isEqualTo("SUSPECTED");
        assertThat(kmmse.scores()).containsEntry("tool", "KMMSE").containsEntry("score", 21)
                .containsEntry("judgment", "SUSPECTED").doesNotContainKey("institution");

        // 경계: 0점과 30점
        assertThat(save(kim, scores("CIST", 0, "IMPAIRED", null), DAY.plusDays(1)).total()).isZero();
        assertThat(save(kim, scores("CIST", 30, "NORMAL", null), DAY.plusDays(2)).total()).isEqualTo(30);

        // 치매 진단은 점수 없이 — total 0, 기본 판정은 직원이 고른 값
        LabRiskDto.View diagnosis = save(kim, scores("diagnosis", null, "impaired", null), DAY.plusDays(3));
        assertThat(diagnosis.total()).isZero();
        assertThat(diagnosis.riskLevel()).isEqualTo("IMPAIRED");
        assertThat(diagnosis.scores()).containsEntry("tool", "DIAGNOSIS").doesNotContainKey("score");

        // 외부기관 결과는 기관명과 함께
        LabRiskDto.View external = save(kim, scores("EXTERNAL", null, "NORMAL", " 숲속치매안심센터 "), DAY.plusDays(4));
        assertThat(external.scores()).containsEntry("institution", "숲속치매안심센터");

        // 이력은 쌓이고 조회된다
        assertThat(service.listByElder(kim.getId())).hasSize(5);

        // 알 수 없는 도구·판정·키는 400
        assertBad(scores("MMSE", 20, "NORMAL", null));
        assertBad(scores("KMMSE", 20, "GOOD", null));
        assertBad(scores("KMMSE", 20, null, null));
        assertBad(scores(null, 20, "NORMAL", null));
        Map<String, Object> extra = scores("KMMSE", 20, "NORMAL", null);
        extra.put("memo", "x");
        assertBad(extra);
    }

    // KMMSE·CIST는 0~30점 정수 점수가 필수 — 빠지거나 범위 밖이거나 정수가 아니면 400
    @Test
    void kmmseRequiresScore() {
        assertBad(scores("KMMSE", null, "NORMAL", null));
        assertBad(scores("CIST", null, "NORMAL", null));
        assertBad(scores("KMMSE", -1, "NORMAL", null));
        assertBad(scores("KMMSE", 31, "NORMAL", null));
        assertBad(scores("CIST", 24.5, "NORMAL", null));
        assertBad(scores("KMMSE", "24", "NORMAL", null));
        assertBad(scores("KMMSE", 1e30, "NORMAL", null));
        // 점수가 있어도 저장되지 않았다
        assertThat(service.listByElder(kim.getId())).isEmpty();
        // 외부·진단 도구에 점수를 곁들이면 같은 범위를 지켜야 한다
        assertBad(scores("EXTERNAL", 45, "NORMAL", "병원"));
        assertThat(save(kim, scores("EXTERNAL", 25, "NORMAL", "병원"), DAY).total()).isEqualTo(25);
    }

    // 외부기관 결과는 기관명이 필수이고 50자까지
    @Test
    void externalRequiresInstitution() {
        assertBad(scores("EXTERNAL", null, "NORMAL", null));
        assertBad(scores("EXTERNAL", null, "NORMAL", "   "));
        assertBad(scores("EXTERNAL", null, "NORMAL", "가".repeat(51)));
        assertBad(scores("EXTERNAL", null, "NORMAL", 123));
        assertThat(save(kim, scores("EXTERNAL", null, "SUSPECTED", "가".repeat(50)), DAY).riskLevel())
                .isEqualTo("SUSPECTED");
        assertThat(service.listByElder(kim.getId())).hasSize(1);
    }

    // latest에 cognitive가 더해지고, 평가가 없는 분은 null. 욕창·낙상과 섞이지 않는다
    @Test
    void latestIncludesCognitive() {
        save(kim, scores("KMMSE", 17, "IMPAIRED", null), DAY);
        save(kim, scores("KMMSE", 26, "NORMAL", null), DAY.plusDays(20));

        List<LabRiskDto.Latest> latest = service.latest();
        LabRiskDto.Latest forKim = latest.stream().filter(l -> l.elderlyId().equals(kim.getId())).findFirst().orElseThrow();
        LabRiskDto.Latest forPark = latest.stream().filter(l -> l.elderlyId().equals(park.getId())).findFirst().orElseThrow();

        assertThat(forKim.cognitive()).isNotNull();
        assertThat(forKim.cognitive().assessedOn()).isEqualTo(DAY.plusDays(20));
        assertThat(forKim.cognitive().total()).isEqualTo(26);
        assertThat(forKim.cognitive().riskLevel()).isEqualTo("NORMAL");
        assertThat(forKim.braden()).isNull();
        assertThat(forKim.morse()).isNull();
        assertThat(forPark.cognitive()).isNull();

        // 같은 날 두 번 평가하면 나중 것이 최신
        save(park, scores("KMMSE", 20, "SUSPECTED", null), DAY);
        save(park, scores("KMMSE", 29, "NORMAL", null), DAY);
        LabRiskDto.Latest parkNow = service.latest().stream().filter(l -> l.elderlyId().equals(park.getId()))
                .findFirst().orElseThrow();
        assertThat(parkNow.cognitive().total()).isEqualTo(29);
    }

    // 욕창 평가 종류 오류 문구에 인지기능이 포함된다
    @Test
    void unknownKindMessageListsCognitive() {
        assertThatThrownBy(() -> service.create(new LabRiskDto.Input(kim.getId(), "FOO", Map.of(), DAY, null, null,
                null))).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getMessage()).contains("COGNITIVE"));
    }
}
