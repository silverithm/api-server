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
class LabRiskAssessmentServiceTest {

    @Autowired private LabRiskAssessmentService service;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private static Map<String, Number> braden(int sensory, int moisture, int activity, int mobility,
                                              int nutrition, int friction) {
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("sensory", sensory);
        m.put("moisture", moisture);
        m.put("activity", activity);
        m.put("mobility", mobility);
        m.put("nutrition", nutrition);
        m.put("friction", friction);
        return m;
    }

    private static Map<String, Number> morse(int history, int secondary, int aid, int iv, int gait, int mental) {
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("history", history);
        m.put("secondaryDiagnosis", secondary);
        m.put("ambulatoryAid", aid);
        m.put("ivTherapy", iv);
        m.put("gait", gait);
        m.put("mentalStatus", mental);
        return m;
    }

    private LabRiskDto.View save(Elderly elder, String kind, Map<String, Number> scores, LocalDate on) {
        return service.create(new LabRiskDto.Input(elder.getId(), kind, scores, on, "박간호", null, false));
    }

    // Braden 합계와 단계는 서버가 항목 점수에서 계산한다
    @Test
    void bradenTotalAndLevelComputedOnServer() {
        assertThat(save(kim, "BRADEN", braden(3, 3, 3, 3, 3, 2), DAY).total()).isEqualTo(17);
        assertThat(save(kim, "BRADEN", braden(3, 3, 3, 3, 3, 2), DAY).riskLevel()).isEqualTo("MILD");

        // 경계값: 합계 6~23 전 구간의 단계 경계
        assertThat(LabRiskScoring.bradenLevel(9)).isEqualTo("VERY_HIGH");
        assertThat(LabRiskScoring.bradenLevel(10)).isEqualTo("HIGH");
        assertThat(LabRiskScoring.bradenLevel(12)).isEqualTo("HIGH");
        assertThat(LabRiskScoring.bradenLevel(13)).isEqualTo("MODERATE");
        assertThat(LabRiskScoring.bradenLevel(14)).isEqualTo("MODERATE");
        assertThat(LabRiskScoring.bradenLevel(15)).isEqualTo("MILD");
        assertThat(LabRiskScoring.bradenLevel(18)).isEqualTo("MILD");
        assertThat(LabRiskScoring.bradenLevel(19)).isEqualTo("NONE");

        LabRiskDto.View worst = save(kim, "BRADEN", braden(1, 1, 1, 1, 1, 1), DAY);
        assertThat(worst.total()).isEqualTo(6);
        assertThat(worst.riskLevel()).isEqualTo("VERY_HIGH");
        LabRiskDto.View best = save(kim, "BRADEN", braden(4, 4, 4, 4, 4, 3), DAY);
        assertThat(best.total()).isEqualTo(23);
        assertThat(best.riskLevel()).isEqualTo("NONE");
        assertThat(best.scores()).containsEntry("friction", 3).hasSize(6);
    }

    // Morse 합계와 단계는 서버가 항목 점수에서 계산한다
    @Test
    void morseTotalAndLevelComputedOnServer() {
        LabRiskDto.View high = save(kim, "MORSE", morse(25, 15, 0, 0, 10, 0), DAY);
        assertThat(high.total()).isEqualTo(50);
        assertThat(high.riskLevel()).isEqualTo("HIGH");

        assertThat(save(kim, "MORSE", morse(0, 0, 0, 0, 0, 0), DAY).riskLevel()).isEqualTo("LOW");
        assertThat(LabRiskScoring.morseLevel(24)).isEqualTo("LOW");
        assertThat(LabRiskScoring.morseLevel(25)).isEqualTo("MODERATE");
        assertThat(LabRiskScoring.morseLevel(44)).isEqualTo("MODERATE");
        assertThat(LabRiskScoring.morseLevel(45)).isEqualTo("HIGH");

        LabRiskDto.View max = save(kim, "morse", morse(25, 15, 30, 20, 20, 15), DAY);
        assertThat(max.total()).isEqualTo(125);
        assertThat(max.kind()).isEqualTo("MORSE");
    }

    // 허용되지 않는 점수는 400
    @Test
    void outOfRangeScoreRejected() {
        assertBad(() -> save(kim, "BRADEN", braden(5, 3, 3, 3, 3, 2), DAY));
        assertBad(() -> save(kim, "BRADEN", braden(3, 3, 3, 3, 3, 4), DAY));
        assertBad(() -> save(kim, "BRADEN", braden(0, 3, 3, 3, 3, 2), DAY));
        // Morse는 구간이 아니라 정해진 값만 가능하다
        assertBad(() -> save(kim, "MORSE", morse(25, 15, 0, 0, 15, 0), DAY));
        assertBad(() -> save(kim, "MORSE", morse(10, 15, 0, 0, 10, 0), DAY));

        Map<String, Number> fractional = braden(3, 3, 3, 3, 3, 2);
        fractional.put("sensory", 3.5);
        assertBad(() -> save(kim, "BRADEN", fractional, DAY));
        assertBad(() -> save(kim, "BRADEN", braden(3, 3, 3, 3, 3, 2), LocalDate.now().plusDays(3)));
        assertThat(service.listByElder(kim.getId())).isEmpty();
    }

    // 항목이 빠졌거나 모르는 항목이 있으면 400
    @Test
    void missingItemRejected() {
        Map<String, Number> missing = braden(3, 3, 3, 3, 3, 2);
        missing.remove("friction");
        assertBad(() -> save(kim, "BRADEN", missing, DAY));

        Map<String, Number> unknown = braden(3, 3, 3, 3, 3, 2);
        unknown.put("mystery", 1);
        assertBad(() -> save(kim, "BRADEN", unknown, DAY));

        assertBad(() -> save(kim, "BRADEN", null, DAY));
        assertBad(() -> save(kim, "FALLS", braden(3, 3, 3, 3, 3, 2), DAY));
        // Braden 항목을 Morse에 넣어도 빠진 항목이 있는 것이다
        assertBad(() -> save(kim, "MORSE", braden(3, 3, 3, 3, 3, 2), DAY));
    }

    // latest는 평가를 한 번도 안 한 어르신도 포함하고, 종류별 가장 최근 평가를 담는다
    @Test
    void latestIncludesNeverAssessedElders() {
        save(kim, "BRADEN", braden(3, 3, 3, 3, 3, 2), LocalDate.of(2026, 4, 1));
        save(kim, "BRADEN", braden(1, 1, 1, 1, 1, 1), LocalDate.of(2026, 10, 1));
        save(kim, "MORSE", morse(0, 0, 0, 0, 0, 0), LocalDate.of(2026, 9, 1));
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        List<LabRiskDto.Latest> latest = service.latest();

        assertThat(latest).extracting(LabRiskDto.Latest::elderlyName).containsExactly("김순자", "박영수");
        LabRiskDto.Latest kimLatest = latest.get(0);
        assertThat(kimLatest.braden().total()).isEqualTo(6);
        assertThat(kimLatest.braden().assessedOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(kimLatest.morse().riskLevel()).isEqualTo("LOW");
        LabRiskDto.Latest parkLatest = latest.get(1);
        assertThat(parkLatest.elderlyId()).isEqualTo(park.getId());
        assertThat(parkLatest.braden()).isNull();
        assertThat(parkLatest.morse()).isNull();
        assertThat(latest).noneMatch(l -> l.elderlyId().equals(stranger.getId()));

        // 이력은 덮어쓰지 않고 쌓이며 최신순이다
        assertThat(service.listByElder(kim.getId())).extracting(LabRiskDto.View::assessedOn)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 4, 1));
    }

    // 다른 기관 어르신은 평가할 수도 이력을 볼 수도 없다
    @Test
    void otherCompanyElderRejected() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertThatThrownBy(() -> save(stranger, "BRADEN", braden(3, 3, 3, 3, 3, 2), DAY))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.listByElder(stranger.getId())).isInstanceOf(CustomException.class);
    }

    private static void assertBad(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
