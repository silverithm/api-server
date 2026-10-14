package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCarePlanRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabCarePlanServiceTest {

    @Autowired private LabCarePlanService service;
    @Autowired private LabCarePlanRepository plans;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private static final LocalDate ASSESSED = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        access.companyId = forest.getId();
    }

    private LabCarePlanDto.Needs needs(String physical) {
        return new LabCarePlanDto.Needs(physical, "경도 인지저하", "연하 곤란 없음", "청력 저하", "배우자와 거주", null);
    }

    private LabCarePlanDto.Input input(int year, String goals, List<LabCarePlanDto.ServiceItem> services,
                                       LocalDate agreed) {
        return new LabCarePlanDto.Input(kim.getId(), year, ASSESSED, needs("보행기 사용"), goals, services, agreed,
                "박사회복지사", true);
    }

    private static List<LabCarePlanDto.ServiceItem> services(int count) {
        List<LabCarePlanDto.ServiceItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new LabCarePlanDto.ServiceItem("신체활동지원", "보행 보조 " + i, "매일"));
        }
        return items;
    }

    // 어르신·연도마다 하나 — 다시 저장하면 갱신이고, 다른 해는 새로 생긴다
    @Test
    void upsertByElderAndYear() {
        LabCarePlanDto.View first = service.upsert(input(2026, "낙상 예방", services(2), null));
        LabCarePlanDto.View second = service.upsert(input(2026, "낙상 예방 및 인지 유지", services(3),
                LocalDate.of(2026, 1, 20)));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(plans.count()).isEqualTo(1);
        assertThat(second.goals()).isEqualTo("낙상 예방 및 인지 유지");
        assertThat(second.services()).hasSize(3);
        assertThat(second.guardianAgreedOn()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(second.elderlyName()).isEqualTo("김순자");
        assertThat(second.planYear()).isEqualTo(2026);

        service.upsert(input(2027, "새해 계획", null, null));
        assertThat(plans.count()).isEqualTo(2);
        assertThat(service.listByYear("2026")).hasSize(1);
        assertThat(service.listByYear("2027").get(0).services()).isEmpty();
        assertThat(service.listByYear("2025")).isEmpty();

        // 다른 기관 어르신은 404, 연도 범위 밖은 400
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        assertThatThrownBy(() -> service.upsert(new LabCarePlanDto.Input(stranger.getId(), 2026, ASSESSED, null,
                null, null, null, null, null))).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.upsert(input(1999, "x", null, null))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert(input(40000, "x", null, null))).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert(new LabCarePlanDto.Input(kim.getId(), null, ASSESSED, null, null,
                null, null, null, null))).isInstanceOf(CustomException.class);

        // 삭제는 자기 기관만
        access.companyId = forest.getId() + 1000;
        assertThatThrownBy(() -> service.delete(first.id())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        access.companyId = forest.getId();
        service.delete(first.id());
        assertThat(plans.count()).isEqualTo(1);
    }

    // 급여 내용은 30개까지 — 31개는 400. 빈 줄은 버리고, 영역만 적은 줄은 400
    @Test
    void servicesLimitEnforced() {
        assertThat(service.upsert(input(2026, "x", services(30), null)).services()).hasSize(30);
        assertThatThrownBy(() -> service.upsert(input(2026, "x", services(31), null)))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("30");
                });
        // 실패한 저장은 기존 계획(30개)을 건드리지 않는다
        assertThat(service.listByYear("2026").get(0).services()).hasSize(30);

        List<LabCarePlanDto.ServiceItem> withBlank = List.of(new LabCarePlanDto.ServiceItem(" ", null, ""),
                new LabCarePlanDto.ServiceItem("인지", "회상 훈련", "주 3회"));
        assertThat(service.upsert(input(2026, "x", withBlank, null)).services()).hasSize(1);
        assertThatThrownBy(() -> service.upsert(input(2026, "x",
                List.of(new LabCarePlanDto.ServiceItem("인지", null, "주 3회")), null)))
                .isInstanceOf(CustomException.class);
    }

    // 욕구(JSON)·목표·급여 내용이 그대로 돌아오고, DB에는 암호문으로 들어간다.
    // 칸 상한을 모두 채운 최대 크기도 TEXT(64KB)에 들어간다
    @Test
    void needsRoundTrip() {
        service.upsert(input(2026, "보행 안정과 인지 유지", services(2), null));
        em.flush();
        em.clear();

        LabCarePlanDto.View read = service.listByYear("2026").get(0);
        assertThat(read.needs().physical()).isEqualTo("보행기 사용");
        assertThat(read.needs().cognition()).isEqualTo("경도 인지저하");
        assertThat(read.needs().other()).isNull();
        assertThat(read.services().get(1).content()).isEqualTo("보행 보조 1");

        for (String column : List.of("needs", "goals", "services")) {
            String stored = jdbc.queryForObject("SELECT " + column + " FROM lab_care_plans", String.class);
            assertThat(stored).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT needs FROM lab_care_plans", String.class)).doesNotContain("보행기");

        // 최대 크기 — 영역 30자·내용 300자·빈도 40자를 30줄, 욕구 여섯 칸 1000자씩
        List<LabCarePlanDto.ServiceItem> max = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            max.add(new LabCarePlanDto.ServiceItem("가".repeat(30), "나".repeat(300), "다".repeat(40)));
        }
        String big = "라".repeat(1000);
        service.upsert(new LabCarePlanDto.Input(kim.getId(), 2026, ASSESSED,
                new LabCarePlanDto.Needs(big, big, big, big, big, big), "마".repeat(2000), max, null, null, null));
        em.flush();
        em.clear();
        Integer servicesLength = jdbc.queryForObject("SELECT LENGTH(services) FROM lab_care_plans", Integer.class);
        assertThat(servicesLength).isLessThan(65_535);
        assertThat(service.listByYear("2026").get(0).services()).hasSize(30);
        assertThat(service.listByYear("2026").get(0).needs().family()).hasSize(1000);

        // 한 칸이 상한을 넘으면 400
        assertThatThrownBy(() -> service.upsert(input(2026, "x",
                List.of(new LabCarePlanDto.ServiceItem("가", "나".repeat(301), null)), null)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.upsert(new LabCarePlanDto.Input(kim.getId(), 2026, ASSESSED,
                new LabCarePlanDto.Needs("가".repeat(1001), null, null, null, null, null), null, null, null, null,
                null))).isInstanceOf(CustomException.class);
    }
}
