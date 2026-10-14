package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffTrainingDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffTrainingRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabStaffTrainingServiceTest {

    @Autowired private LabStaffTrainingService service;
    @Autowired private LabStaffTrainingRepository trainings;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member lee;
    private Member han;
    private static final LocalDate DAY = LocalDate.of(2026, 3, 10);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        lee = LabTestFixtures.member(em, forest, "이요양", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        han = LabTestFixtures.member(em, forest, "한사무", Member.Role.OFFICE, Member.MemberStatus.ACTIVE);
        access.companyId = forest.getId();
    }

    private LabStaffTrainingDto.Input input(String kind, String category, List<Long> ids) {
        return new LabStaffTrainingDto.Input(kind, DAY, category, "노인학대 예방 교육", "외부 강사", ids,
                "신고 의무와 사례를 다뤘다", false);
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    // 참석자는 모두 같은 기관 직원이어야 한다 — 다른 기관 직원이 하나라도 섞이면 통째로 거절
    @Test
    void attendeesMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member stranger = LabTestFixtures.member(em, other, "남직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);

        assertStatus(() -> service.create(input("TRAINING", "ELDER_ABUSE", List.of(lee.getId(), stranger.getId()))),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.create(input("TRAINING", "ELDER_ABUSE", List.of(999_999L))), HttpStatus.NOT_FOUND);
        assertThat(trainings.count()).isZero();

        LabStaffTrainingDto.View ok = service.create(
                input("TRAINING", "ELDER_ABUSE", List.of(han.getId(), lee.getId(), han.getId())));
        // 이름은 서버가 입력 순서대로 채우고 중복은 합친다
        assertThat(ok.attendeeIds()).containsExactly(han.getId(), lee.getId());
        assertThat(ok.attendeeNames()).containsExactly("한사무", "이요양");
        assertStatus(() -> service.update(ok.id(), input("TRAINING", "ELDER_ABUSE", List.of(stranger.getId()))),
                HttpStatus.NOT_FOUND);

        // 참석자 이름을 연도 목록에서 한 번에 채운다. 비활성 직원의 지난 이력도 이름이 남는다
        Member left = LabTestFixtures.member(em, forest, "퇴사자", Member.Role.CAREGIVER,
                Member.MemberStatus.INACTIVE);
        service.create(input("MEETING", "CASE", List.of(left.getId())));
        List<LabStaffTrainingDto.View> year = service.listByYear("2026");
        assertThat(year).hasSize(2);
        assertThat(year.get(1).attendeeNames()).containsExactly("퇴사자");

        // 내용은 암호화 열
        em.flush();
        em.clear();
        assertThat(jdbc.queryForObject("SELECT content FROM lab_staff_trainings WHERE kind = 'TRAINING'",
                String.class)).startsWith(EncryptedPiiConverter.ENC_PREFIX).doesNotContain("신고");
    }

    // kind와 category 조합이 맞아야 하고, 연도 경계를 지킨다
    @Test
    void categoryMustMatchKind() {
        assertStatus(() -> service.create(input("MEETING", "ELDER_ABUSE", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input("TRAINING", "CASE", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input("TRAINING", "STAFF", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input("SEMINAR", "OTHER", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input("TRAINING", null, null)), HttpStatus.BAD_REQUEST);
        assertThat(trainings.count()).isZero();

        for (String category : List.of("ELDER_ABUSE", "SEXUAL_HARASSMENT", "INFECTION", "SAFETY", "PRIVACY",
                "DISABILITY_AWARENESS", "JOB", "OTHER")) {
            assertThat(service.create(input("TRAINING", category, null)).category()).isEqualTo(category);
        }
        for (String category : List.of("CASE", "STAFF", "OTHER")) {
            assertThat(service.create(input("MEETING", category, null)).kind()).isEqualTo("MEETING");
        }

        // 연도 경계: 12/31과 1/1은 서로 다른 해
        trainings.deleteAll();
        service.create(new LabStaffTrainingDto.Input("TRAINING", LocalDate.of(2025, 12, 31), "SAFETY", "안전", null,
                null, null, null));
        service.create(new LabStaffTrainingDto.Input("TRAINING", LocalDate.of(2026, 1, 1), "SAFETY", "안전", null,
                null, null, null));
        service.create(new LabStaffTrainingDto.Input("TRAINING", LocalDate.of(2026, 12, 31), "SAFETY", "안전", null,
                null, null, null));
        service.create(new LabStaffTrainingDto.Input("TRAINING", LocalDate.of(2027, 1, 1), "SAFETY", "안전", null,
                null, null, null));
        assertThat(service.listByYear("2026")).extracting(LabStaffTrainingDto.View::heldOn)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertStatus(() -> service.listByYear("26"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.listByYear("abcd"), HttpStatus.BAD_REQUEST);
    }

    // 참석자는 최대 200명
    @Test
    void attendeeLimit() {
        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        for (long i = 1; i <= 201; i++) {
            ids.add(i);
        }
        assertStatus(() -> service.create(input("TRAINING", "SAFETY", ids)), HttpStatus.BAD_REQUEST);
    }
}
