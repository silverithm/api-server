package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabProgramRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@LabJpaTest
class LabProgramServiceTest {

    @Autowired private LabProgramService service;
    @Autowired private LabProgramRepository programs;
    @Autowired private EntityManager em;
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

    private LabProgramDto.Input input(LocalDate date, String time, List<Long> ids) {
        return new LabProgramDto.Input(date, time, "COGNITIVE", "회상 훈련", "기억력 자극", "옛 사진 보며 이야기 나누기",
                "대체로 즐겁게 참여", "최강사", ids, true);
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getHttpStatus()).isEqualTo(status));
    }

    // 다른 기관 어르신이 하나라도 섞이면 통째로 404이고 아무것도 저장되지 않는다
    @Test
    void participantsMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");
        LocalDate day = LocalDate.of(2026, 10, 14);

        assertStatus(() -> service.create(input(day, "14:00", List.of(kim.getId(), stranger.getId()))),
                HttpStatus.NOT_FOUND);
        assertStatus(() -> service.create(input(day, "14:00", List.of(stranger.getId()))), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.create(input(day, "14:00", List.of(999_999L))), HttpStatus.NOT_FOUND);
        assertThat(programs.count()).isZero();

        // 수정으로 남의 어르신을 끼워 넣는 것도 막힌다
        LabProgramDto.View ok = service.create(input(day, "14:00", List.of(kim.getId())));
        assertStatus(() -> service.update(ok.id(), input(day, "14:00", List.of(kim.getId(), stranger.getId()))),
                HttpStatus.NOT_FOUND);
        assertThat(service.listByMonth("2026-10").get(0).participantIds()).containsExactly(kim.getId());
    }

    // 이름은 서버가 채우고, 입력 순서·중복 제거·최대 100명을 지킨다
    @Test
    void participantNamesFilled() {
        LocalDate day = LocalDate.of(2026, 10, 14);
        LabProgramDto.View created = service.create(
                input(day, "09:30", List.of(park.getId(), kim.getId(), park.getId())));

        assertThat(created.participantIds()).containsExactly(park.getId(), kim.getId());
        assertThat(created.participantNames()).containsExactly("박영수", "김순자");
        assertThat(created.aiAssisted()).isTrue();

        LabProgramDto.View updated = service.update(created.id(), input(day, "10:00", List.of(kim.getId())));
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.participantNames()).containsExactly("김순자");
        assertThat(updated.startTime()).isEqualTo("10:00");
        assertThat(programs.count()).isEqualTo(1);

        // 참여자 없이도 저장되고, 어르신이 사라진 id는 목록에서 걸러진다
        LabProgramDto.View empty = service.create(input(day, null, null));
        assertThat(empty.participantIds()).isEmpty();
        assertThat(empty.participantNames()).isEmpty();

        List<Long> many = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            many.add(i);
        }
        assertStatus(() -> service.create(input(day, null, many)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(day, "25:00", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(day, "9:30", null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(input(day, null, java.util.Arrays.asList(kim.getId(), null))),
                HttpStatus.BAD_REQUEST);
    }

    // 정확히 100명은 저장된다
    @Test
    void hundredParticipantsAccepted() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(LabTestFixtures.elder(em, forest, "어르신" + i).getId());
        }
        assertThat(service.create(input(LocalDate.of(2026, 10, 2), null, ids)).participantIds()).hasSize(100);
    }

    // 월 경계 — 1일과 말일은 포함, 전달 말일·다음 달 1일은 제외. 날짜순·시각순 정렬
    @Test
    void monthFilterReturnsOnlyThatMonth() {
        service.create(input(LocalDate.of(2026, 9, 30), "10:00", null));
        service.create(input(LocalDate.of(2026, 10, 1), "10:00", null));
        service.create(input(LocalDate.of(2026, 10, 31), "09:00", null));
        service.create(input(LocalDate.of(2026, 10, 31), "08:00", null));
        service.create(input(LocalDate.of(2026, 11, 1), "10:00", null));

        List<LabProgramDto.View> october = service.listByMonth("2026-10");
        assertThat(october).extracting(LabProgramDto.View::programDate)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31));
        assertThat(october).extracting(LabProgramDto.View::startTime).containsExactly("10:00", "08:00", "09:00");
        assertThat(service.listByMonth("2026-12")).isEmpty();
        assertStatus(() -> service.listByMonth("2026-13"), HttpStatus.BAD_REQUEST);

        // 다른 기관은 보지도 지우지도 못한다
        Long id = october.get(0).id();
        access.companyId = forest.getId() + 1000;
        assertThat(service.listByMonth("2026-10")).isEmpty();
        assertStatus(() -> service.delete(id), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.update(id, input(LocalDate.of(2026, 10, 1), null, null)), HttpStatus.NOT_FOUND);
        access.companyId = forest.getId();
        service.delete(id);
        assertThat(programs.count()).isEqualTo(4);
    }

    // 분류·필수 값 검증
    @Test
    void validatesInput() {
        LocalDate day = LocalDate.of(2026, 10, 14);
        assertStatus(() -> service.create(new LabProgramDto.Input(day, null, "GAME", "제목", null, null, null,
                null, null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabProgramDto.Input(day, null, "SOCIAL", " ", null, null, null,
                null, null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabProgramDto.Input(null, null, "SOCIAL", "제목", null, null, null,
                null, null, null)), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.create(new LabProgramDto.Input(day, null, "SOCIAL", "가".repeat(101), null, null,
                null, null, null, null)), HttpStatus.BAD_REQUEST);
    }
}
