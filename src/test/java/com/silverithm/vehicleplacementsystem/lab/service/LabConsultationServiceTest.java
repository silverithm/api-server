package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabConsultationDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabConsultationRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabConsultationServiceTest {

    @Autowired private LabConsultationService service;
    @Autowired private LabConsultationRepository consultations;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private LabConsultationDto.Input input(Elderly elder, LocalDate day, String guardian, String content) {
        return new LabConsultationDto.Input(elder.getId(), day, "GUARDIAN", guardian, "PHONE", content,
                "다음 주 방문 일정 조율", "박사회복지사", false);
    }

    // 보호자 이름·상담 내용·후속 조치는 DB에 암호문으로 들어간다 (원시 열 값을 직접 읽는다)
    @Test
    void guardianNameEncryptedAtRest() {
        LabConsultationDto.View saved = service.create(
                input(kim, LocalDate.of(2026, 10, 14), "김철수", "낙상 후 보행 상태 상담"));
        assertThat(saved.counterpartName()).isEqualTo("김철수");
        em.flush();
        em.clear();

        for (String column : List.of("counterpart_name", "content", "follow_up")) {
            String stored = jdbc.queryForObject("SELECT " + column + " FROM lab_consultations", String.class);
            assertThat(stored).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT counterpart_name FROM lab_consultations", String.class))
                .doesNotContain("김철수");

        LabConsultationDto.View read = service.list(kim.getId(), null).get(0);
        assertThat(read.counterpartName()).isEqualTo("김철수");
        assertThat(read.content()).isEqualTo("낙상 후 보행 상태 상담");
        assertThat(read.followUp()).isEqualTo("다음 주 방문 일정 조율");

        // 평문 50자의 보호자 이름은 VARCHAR(512)에 암호문으로 들어간다
        LabConsultationDto.View longest = service.create(
                input(kim, LocalDate.of(2026, 10, 15), "가".repeat(50), "내용"));
        em.flush();
        assertThat(longest.counterpartName()).hasSize(50);
        assertThat(jdbc.queryForObject("SELECT MAX(LENGTH(counterpart_name)) FROM lab_consultations",
                Integer.class)).isLessThanOrEqualTo(512);
    }

    // 다른 기관 어르신은 등록·수정·조회 모두 404이고, 다른 기관은 지울 수도 없다
    @Test
    void otherCompanyElderRejected() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertThatThrownBy(() -> service.create(input(stranger, LocalDate.of(2026, 10, 14), null, "내용")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.list(stranger.getId(), null)).isInstanceOf(CustomException.class);
        assertThat(consultations.count()).isZero();

        LabConsultationDto.View mine = service.create(input(kim, LocalDate.of(2026, 10, 14), null, "내용"));
        assertThatThrownBy(() -> service.update(mine.id(), input(stranger, LocalDate.of(2026, 10, 14), null, "x")))
                .isInstanceOf(CustomException.class);
        access.companyId = forest.getId() + 1000;
        assertThatThrownBy(() -> service.delete(mine.id())).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.list(null, "2026-10")).isEmpty();
    }

    // 최신순, 어르신별·월별 필터와 월 경계
    @Test
    void listsNewestFirstWithBoundaries() {
        service.create(input(kim, LocalDate.of(2026, 9, 30), null, "전달"));
        service.create(input(kim, LocalDate.of(2026, 10, 1), null, "초"));
        service.create(input(kim, LocalDate.of(2026, 10, 31), null, "말"));
        service.create(input(kim, LocalDate.of(2026, 10, 31), null, "말 두 번째"));
        service.create(input(kim, LocalDate.of(2026, 11, 1), null, "다음달"));
        service.create(input(park, LocalDate.of(2026, 10, 15), null, "박"));

        assertThat(service.list(kim.getId(), null)).extracting(LabConsultationDto.View::content)
                .containsExactly("다음달", "말 두 번째", "말", "초", "전달");
        assertThat(service.list(kim.getId(), "2026-10")).extracting(LabConsultationDto.View::content)
                .containsExactly("말 두 번째", "말", "초");
        List<LabConsultationDto.View> october = service.list(null, "2026-10");
        assertThat(october).extracting(LabConsultationDto.View::content)
                .containsExactly("말 두 번째", "말", "박", "초");
        assertThat(october.get(2).elderlyName()).isEqualTo("박영수");

        assertThatThrownBy(() -> service.list(null, null)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // 수정은 내용을 덮어쓰고, 잘못된 분류·빈 내용·긴 이름은 400
    @Test
    void updateAndValidation() {
        LabConsultationDto.View saved = service.create(input(kim, LocalDate.of(2026, 10, 14), "김철수", "처음"));
        LabConsultationDto.View edited = service.update(saved.id(), input(kim, LocalDate.of(2026, 10, 14), null, "고침"));
        assertThat(edited.id()).isEqualTo(saved.id());
        assertThat(edited.counterpartName()).isNull();
        assertThat(edited.content()).isEqualTo("고침");
        assertThat(consultations.count()).isEqualTo(1);

        LocalDate day = LocalDate.of(2026, 10, 14);
        for (LabConsultationDto.Input bad : List.of(
                new LabConsultationDto.Input(kim.getId(), day, "NEIGHBOR", null, "PHONE", "x", null, null, null),
                new LabConsultationDto.Input(kim.getId(), day, "SELF", null, "FAX", "x", null, null, null),
                new LabConsultationDto.Input(kim.getId(), day, "SELF", null, "PHONE", "  ", null, null, null),
                new LabConsultationDto.Input(kim.getId(), day, "SELF", "가".repeat(51), "PHONE", "x", null, null, null),
                new LabConsultationDto.Input(kim.getId(), null, "SELF", null, "PHONE", "x", null, null, null))) {
            assertThatThrownBy(() -> service.create(bad)).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
