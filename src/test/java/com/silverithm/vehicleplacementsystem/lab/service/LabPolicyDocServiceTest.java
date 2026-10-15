package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabPolicyDocDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabPolicyDocRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabPolicyDocServiceTest {

    @Autowired private LabPolicyDocService service;
    @Autowired private LabPolicyDocRepository docs;
    @Autowired private EntityManager em;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private static LabPolicyDocDto.Input doc(String kind, Integer guideNo, String title) {
        return new LabPolicyDocDto.Input(kind, guideNo, title, LocalDate.of(2026, 1, 5), "사무실 서류함",
                Map.of("capacity", true, "contract", false), LocalDate.of(2026, 2, 1), null);
    }

    private static LabPolicyDocDto.Input withChecks(Map<String, Boolean> checks) {
        return new LabPolicyDocDto.Input("OTHER", null, "기타 문서", null, null, checks, null, null);
    }

    // 같은 종류의 두 번째 문서는 409 — 수정으로 다른 문서의 종류와 같아지는 것도 막고, 기타(OTHER)는 여러 개
    @Test
    void secondSameKindConflict() {
        LabPolicyDocDto.View first = service.create(doc("OPERATING_RULES", null, "운영규정"));
        assertThat(first.checks()).containsEntry("capacity", true).containsEntry("contract", false);
        assertRejected(() -> service.create(doc("OPERATING_RULES", null, "운영규정 2")), HttpStatus.CONFLICT, "이미 대장에");
        assertThat(docs.count()).isEqualTo(1);

        // 다른 종류는 가능, 자기 자신을 다시 저장하는 것도 가능(개정일 갱신)
        LabPolicyDocDto.View infection = service.create(doc("INFECTION_PLAN", null, "감염병 대응계획"));
        LabPolicyDocDto.Input renewed = new LabPolicyDocDto.Input("OPERATING_RULES", null, "운영규정(개정)",
                LocalDate.of(2026, 3, 1), "원장실", Map.of("capacity", true), null, "개정함");
        assertThat(service.update(first.id(), renewed).title()).isEqualTo("운영규정(개정)");
        assertThat(docs.count()).isEqualTo(2);
        assertRejected(() -> service.update(infection.id(), doc("OPERATING_RULES", null, "충돌")), HttpStatus.CONFLICT,
                "이미 대장에");
        assertThat(docs.findById(infection.id()).orElseThrow().getKind()).isEqualTo("INFECTION_PLAN");

        // 기타는 얼마든지, 급여제공지침은 번호마다 하나
        service.create(doc("OTHER", null, "기타 1"));
        service.create(doc("OTHER", null, "기타 2"));
        service.create(doc("CARE_GUIDE", 1, "지침 1"));
        service.create(doc("CARE_GUIDE", 2, "지침 2"));
        assertRejected(() -> service.create(doc("CARE_GUIDE", 1, "지침 1 중복")), HttpStatus.CONFLICT, "1번");
        assertThat(docs.count()).isEqualTo(6);

        // 다른 기관은 같은 종류를 따로 갖는다. 남의 문서는 못 고치고 못 지운다
        access.companyId = LabTestFixtures.company(em, "다른기관").getId();
        assertThat(service.list()).isEmpty();
        service.create(doc("OPERATING_RULES", null, "다른 기관 운영규정"));
        assertRejected(() -> service.update(first.id(), doc("OPERATING_RULES", null, "x")), HttpStatus.NOT_FOUND,
                "찾을 수 없습니다");
        assertRejected(() -> service.delete(first.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();

        // 지우면 같은 종류를 다시 만들 수 있다. 목록은 종류·번호 순
        service.delete(first.id());
        service.create(doc("OPERATING_RULES", null, "운영규정 새로"));
        assertThat(service.list()).extracting(LabPolicyDocDto.View::kind).containsExactly("CARE_GUIDE", "CARE_GUIDE",
                "INFECTION_PLAN", "OPERATING_RULES", "OTHER", "OTHER");
        assertThat(service.list().get(1).guideNo()).isEqualTo(2);
    }

    // 급여제공지침은 번호 1~12 필수, 그 밖의 종류는 번호를 비운다
    @Test
    void careGuideRequiresGuideNo() {
        assertRejected(() -> service.create(doc("CARE_GUIDE", null, "지침")), HttpStatus.BAD_REQUEST, "번호");
        assertRejected(() -> service.create(doc("CARE_GUIDE", 0, "지침")), HttpStatus.BAD_REQUEST, "1~12");
        assertRejected(() -> service.create(doc("CARE_GUIDE", 13, "지침")), HttpStatus.BAD_REQUEST, "1~12");
        assertThat(docs.count()).isZero();
        assertThat(service.create(doc("CARE_GUIDE", 12, "지침 12")).guideNo()).isEqualTo(12);
        assertThat(service.create(doc("CARE_GUIDE", 1, "지침 1")).guideNo()).isEqualTo(1);
        // 번호는 급여제공지침에만 의미가 있다
        LabPolicyDocDto.View privacy = service.create(doc("PRIVACY_POLICY", 5, "처리방침"));
        assertThat(privacy.guideNo()).isNull();
        // 수정으로 종류를 바꿔도 같은 규칙
        assertRejected(() -> service.update(privacy.id(), doc("CARE_GUIDE", null, "처리방침")), HttpStatus.BAD_REQUEST,
                "번호");
        assertThat(service.update(privacy.id(), doc("CARE_GUIDE", 10, "개인정보보호지침")).guideNo()).isEqualTo(10);
        // 제목·종류·날짜·길이
        assertRejected(() -> service.create(doc("OPERATING_RULES", null, " ")), HttpStatus.BAD_REQUEST, "제목");
        assertRejected(() -> service.create(doc("OPERATING_RULES", null, "가".repeat(101))), HttpStatus.BAD_REQUEST,
                "제목");
        assertRejected(() -> service.create(doc("MANUAL", null, "x")), HttpStatus.BAD_REQUEST, "종류");
        LocalDate future = LabValidation.seoulToday().plusDays(1);
        assertRejected(() -> service.create(new LabPolicyDocDto.Input("OPERATING_RULES", null, "x", future, null, null,
                null, null)), HttpStatus.BAD_REQUEST, "개정일");
        assertRejected(() -> service.create(new LabPolicyDocDto.Input("OPERATING_RULES", null, "x", null, null, null,
                future, null)), HttpStatus.BAD_REQUEST, "직원 교육일");
        assertThat(service.create(new LabPolicyDocDto.Input("OPERATING_RULES", null, "x", null, null, null, null, null))
                .checks()).isEmpty();
    }

    // 점검 항목 키는 영문·숫자·_ 40자 이내, 30개 이하, 값은 true/false
    @Test
    void checksKeyFormat() {
        for (String bad : new String[] {"운영 규정", "has space", "dash-key", "a.b", "", "키"}) {
            assertRejected(() -> service.create(withChecks(Map.of(bad, true))), HttpStatus.BAD_REQUEST, "점검 항목 키");
        }
        assertRejected(() -> service.create(withChecks(Map.of("k".repeat(41), true))), HttpStatus.BAD_REQUEST,
                "40자");
        Map<String, Boolean> nullValue = new LinkedHashMap<>();
        nullValue.put("capacity", null);
        assertRejected(() -> service.create(withChecks(nullValue)), HttpStatus.BAD_REQUEST, "capacity");
        Map<String, Boolean> thirtyOne = new LinkedHashMap<>();
        for (int i = 0; i < 31; i++) {
            thirtyOne.put("item_" + i, true);
        }
        assertRejected(() -> service.create(withChecks(thirtyOne)), HttpStatus.BAD_REQUEST, "최대 30개");
        Map<String, Boolean> typed = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            typed.put("Item_" + i, i % 2 == 0);
        }
        typed.remove("Item_29");
        typed.put("k".repeat(40), true);
        assertThat(docs.count()).isZero();

        LabPolicyDocDto.View saved = service.create(withChecks(typed));
        assertThat(saved.checks()).hasSize(30).containsEntry("Item_0", true).containsEntry("Item_1", false)
                .containsEntry("k".repeat(40), true);
        // 저장된 JSON을 다시 읽어도 같다
        assertThat(service.list().get(0).checks()).isEqualTo(saved.checks());
        assertThat(service.update(saved.id(), withChecks(Map.of("a1", false))).checks()).containsExactly(
                Map.entry("a1", false));
    }
}
