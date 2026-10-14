package com.silverithm.vehicleplacementsystem.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.EncryptedPiiConverter;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.silverithm.vehicleplacementsystem.lab.dto.LabRehabSessionDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabRehabSessionRepository;

@LabJpaTest
class LabRehabSessionServiceTest {

    @Autowired private LabRehabSessionService service;
    @Autowired private LabRehabSessionRepository sessions;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly park;
    private final LocalDate day = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김순자");
        park = LabTestFixtures.elder(em, forest, "박영수");
        access.companyId = forest.getId();
    }

    private void assertRejected(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(CustomException.class, e -> {
            assertThat(e.getHttpStatus()).isEqualTo(status);
            if (message != null) {
                assertThat(e.getMessage()).contains(message);
            }
        });
    }

    private LabRehabSessionDto.Input input(Elderly elder, LocalDate date, Integer minutes, List<String> items) {
        return new LabRehabSessionDto.Input(elder.getId(), date, "PHYSICAL_FUNCTION", minutes, items,
                "평행봉 보행 훈련", "힘들어하셨으나 끝까지 하심", "최치료사", false);
    }

    private LabRehabSessionDto.BatchInput batch(List<Long> ids, List<String> items) {
        return new LabRehabSessionDto.BatchInput(ids, day, "ADL", 30, items, "일상동작", "최치료사", true);
    }

    @Test
    void minutesRangeValidated() {
        for (int ok : new int[] {1, 240}) {
            assertThat(service.create(input(kim, day, ok, null)).minutes()).isEqualTo(ok);
        }
        for (Integer bad : new Integer[] {0, -5, 241, null}) {
            assertRejected(() -> service.create(input(kim, day, bad, null)), HttpStatus.BAD_REQUEST, null);
        }
        assertRejected(() -> service.create(new LabRehabSessionDto.Input(kim.getId(), day, "YOGA", 30, null, null,
                null, null, null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.create(input(kim, null, 30, null)), HttpStatus.BAD_REQUEST, null);
        assertThat(sessions.count()).isEqualTo(2);
    }

    @Test
    void batchCreatesAllOrNothing() {
        List<LabRehabSessionDto.View> created = service.createBatch(batch(List.of(kim.getId(), park.getId(),
                kim.getId()), List.of(" 평행봉 ", "", "계단 오르기")));
        // 중복 id는 한 번만, 입력 순서대로
        assertThat(created).extracting(LabRehabSessionDto.View::elderlyName).containsExactly("김순자", "박영수");
        assertThat(created.get(0).items()).containsExactly("평행봉", "계단 오르기");
        assertThat(created.get(0).reaction()).isNull();
        assertThat(created.get(1).aiAssisted()).isTrue();
        assertThat(sessions.count()).isEqualTo(2);

        // 한 명이라도 잘못되면(시간 범위 오류) 아무것도 저장하지 않는다
        assertRejected(() -> service.createBatch(new LabRehabSessionDto.BatchInput(List.of(kim.getId(), park.getId()),
                day, "ADL", 999, null, null, null, null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.createBatch(batch(List.of(), null)), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.createBatch(batch(null, null)), HttpStatus.BAD_REQUEST, null);
        List<Long> fifty = new java.util.ArrayList<>();
        for (long i = 1; i <= 51; i++) {
            fifty.add(i);
        }
        assertRejected(() -> service.createBatch(batch(fifty, null)), HttpStatus.BAD_REQUEST, "50명");
        assertThat(sessions.count()).isEqualTo(2);

        // 어르신별 반응은 나중에 PUT으로 따로 고친다
        LabRehabSessionDto.View edited = service.update(created.get(0).id(), new LabRehabSessionDto.Input(
                kim.getId(), day, "ADL", 30, List.of("평행봉"), "일상동작", "잘 따라 하심", "최치료사", true));
        assertThat(edited.reaction()).isEqualTo("잘 따라 하심");
        assertThat(sessions.count()).isEqualTo(2);

        // 암호화: 내용·반응은 암호문, 항목은 평문 JSON
        em.flush();
        for (String column : List.of("content", "reaction")) {
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM lab_rehab_sessions WHERE id = ?",
                    String.class, edited.id())).as(column).startsWith(EncryptedPiiConverter.ENC_PREFIX);
        }
        assertThat(jdbc.queryForObject("SELECT items FROM lab_rehab_sessions WHERE id = ?", String.class,
                edited.id())).isEqualTo("[\"평행봉\"]");
    }

    @Test
    void batchRejectsOtherCompanyElder() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly stranger = LabTestFixtures.elder(em, other, "이영희");

        assertRejected(() -> service.createBatch(batch(List.of(kim.getId(), stranger.getId(), park.getId()), null)),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(sessions.count()).isZero();
        assertRejected(() -> service.create(input(stranger, day, 30, null)), HttpStatus.NOT_FOUND, null);
        assertRejected(() -> service.list(null, "2026-10", stranger.getId()), HttpStatus.NOT_FOUND, null);
        assertThat(sessions.count()).isZero();

        LabRehabSessionDto.View mine = service.create(input(kim, day, 30, null));
        assertRejected(() -> service.update(mine.id(), input(stranger, day, 30, null)), HttpStatus.NOT_FOUND, null);
        access.companyId = other.getId();
        assertRejected(() -> service.delete(mine.id()), HttpStatus.NOT_FOUND, null);
    }

    @Test
    void itemsLimitValidated() {
        List<String> ten = java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(i -> "항목" + i).toList();
        assertThat(service.create(input(kim, day, 30, ten)).items()).hasSize(10);
        List<String> eleven = new java.util.ArrayList<>(ten);
        eleven.add("항목11");
        assertRejected(() -> service.create(input(kim, day, 30, eleven)), HttpStatus.BAD_REQUEST, "10개");
        assertThat(service.create(input(kim, day, 30, List.of("가".repeat(40)))).items().get(0)).hasSize(40);
        assertRejected(() -> service.create(input(kim, day, 30, List.of("가".repeat(41)))),
                HttpStatus.BAD_REQUEST, "40자");
        // 빈 항목은 세지 않고 버린다
        assertThat(service.create(input(kim, day, 30, java.util.Arrays.asList(null, " ", "걷기"))).items())
                .containsExactly("걷기");
        assertThat(sessions.count()).isEqualTo(3);
    }

    @Test
    void monthAndDateFilters() {
        service.create(input(kim, LocalDate.of(2026, 9, 30), 20, null));
        LabRehabSessionDto.View a = service.create(input(kim, LocalDate.of(2026, 10, 1), 20, null));
        LabRehabSessionDto.View b = service.create(input(kim, LocalDate.of(2026, 10, 1), 30, null)); // 하루 여러 번
        LabRehabSessionDto.View c = service.create(input(park, LocalDate.of(2026, 10, 31), 40, null));
        service.create(input(kim, LocalDate.of(2026, 11, 1), 20, null));

        assertThat(service.list(null, "2026-10", null)).extracting(LabRehabSessionDto.View::id)
                .containsExactly(a.id(), b.id(), c.id());
        assertThat(service.list("2026-10-01", null, null)).extracting(LabRehabSessionDto.View::id)
                .containsExactly(a.id(), b.id());
        assertThat(service.list(null, "2026-10", park.getId())).extracting(LabRehabSessionDto.View::id)
                .containsExactly(c.id());
        assertThat(service.list(null, "2026-10", kim.getId())).hasSize(2);
        assertThat(service.list(null, "2026-10", null).get(2).elderlyName()).isEqualTo("박영수");
        assertRejected(() -> service.list(null, null, null), HttpStatus.BAD_REQUEST, null);
        assertRejected(() -> service.list(null, "2026-13", null), HttpStatus.BAD_REQUEST, null);
    }
}
