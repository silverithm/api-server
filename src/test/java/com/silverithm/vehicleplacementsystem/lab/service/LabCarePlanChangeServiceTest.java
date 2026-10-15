package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCareEventDto;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCarePlanChangeDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCarePlanChangeRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabCarePlanChangeServiceTest {

    private static final LocalDate CHANGED = LocalDate.of(2026, 4, 10);

    @Autowired private LabCarePlanChangeService service;
    @Autowired private LabCareEventService eventService;
    @Autowired private LabCarePlanChangeRepository changes;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Elderly kim;
    private Elderly lee;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        lee = LabTestFixtures.elder(em, forest, "이어르신");
        access.companyId = forest.getId();
    }

    private Long eventOf(Elderly elder) {
        return eventService.create(new LabCareEventDto.Input(elder.getId(), "2026-04-08T10:30", "FALL", "MODERATE",
                "화장실", "미끄러짐", "안정", null, null, null, null, null, null, "OPEN", null, "박간호사", false)).id();
    }

    private LabCarePlanChangeDto.Input input(Long elderlyId, List<String> items, Long eventId, LocalDate explained,
                                             LocalDate signed, LocalDate notified) {
        return new LabCarePlanChangeDto.Input(elderlyId, CHANGED, "FUNCTION_CHANGE", "보행 능력이 떨어짐", items,
                "보행 훈련을 주 3회에서 5회로 늘림", eventId, explained, signed, notified);
    }

    private LabCarePlanChangeDto.Input simple(Long eventId) {
        return input(kim.getId(), List.of("FREQUENCY"), eventId, null, null, null);
    }

    // 관련 상태변화는 같은 기관·같은 어르신 것만 — 다른 어르신·다른 기관·없는 사건은 404
    @Test
    void eventMustBeSameElder() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Elderly outsider = LabTestFixtures.elder(em, other, "외부어르신");
        Long kimEvent = eventOf(kim);
        Long leeEvent = eventOf(lee);
        Long previous = access.companyId;
        access.companyId = other.getId();
        Long foreignEvent = eventOf(outsider);
        access.companyId = previous;

        assertRejected(() -> service.create(simple(leeEvent)), HttpStatus.NOT_FOUND, "상태변화 기록을 찾을 수 없습니다");
        assertRejected(() -> service.create(simple(foreignEvent)), HttpStatus.NOT_FOUND,
                "상태변화 기록을 찾을 수 없습니다");
        assertRejected(() -> service.create(simple(999_999L)), HttpStatus.NOT_FOUND, "상태변화 기록을 찾을 수 없습니다");
        assertRejected(() -> service.create(input(outsider.getId(), List.of("TIME"), null, null, null, null)),
                HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        assertThat(changes.count()).isZero();

        LabCarePlanChangeDto.View saved = service.create(simple(kimEvent));
        assertThat(saved.eventId()).isEqualTo(kimEvent);
        assertThat(saved.elderlyName()).isEqualTo("김어르신");
        assertThat(saved.writerName()).isEqualTo("숲속 관리자");
        assertThat(saved.changeDetail()).isEqualTo("보행 훈련을 주 3회에서 5회로 늘림");
        assertEncryptedAtRest(jdbc, "lab_care_plan_changes", "change_detail", "보행 훈련");
        // 고칠 때도 같은 규칙, 사건 없이도 저장된다
        assertRejected(() -> service.update(saved.id(), simple(leeEvent)), HttpStatus.NOT_FOUND, "상태변화");
        assertThat(service.create(simple(null)).eventId()).isNull();

        // 조회: 어르신별·연도별, 하나는 꼭 필요
        assertThat(service.list(kim.getId(), null)).hasSize(2);
        assertThat(service.list(null, "2026")).hasSize(2);
        assertThat(service.list(lee.getId(), null)).isEmpty();
        assertThat(service.list(kim.getId(), "2025")).isEmpty();
        assertRejected(() -> service.list(null, null), HttpStatus.BAD_REQUEST, "elderlyId 또는 year");
        assertRejected(() -> service.list(outsider.getId(), null), HttpStatus.NOT_FOUND, "어르신");

        // 다른 기관은 보지도 고치지도 지우지도 못한다
        access.companyId = other.getId();
        assertThat(service.list(null, "2026")).isEmpty();
        assertRejected(() -> service.delete(saved.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();
        service.delete(saved.id());
        assertThat(changes.count()).isEqualTo(1);
    }

    // 변경 항목은 내용·시간·횟수 중 1~3개, 정한 키만, 중복 없이. 사유 설명·변경 내용은 필수
    @Test
    void changeItemsRequired() {
        assertRejected(() -> service.create(input(kim.getId(), null, null, null, null, null)), HttpStatus.BAD_REQUEST,
                "변경 항목");
        assertRejected(() -> service.create(input(kim.getId(), List.of(), null, null, null, null)),
                HttpStatus.BAD_REQUEST, "변경 항목");
        assertRejected(() -> service.create(input(kim.getId(), List.of("PRICE"), null, null, null, null)),
                HttpStatus.BAD_REQUEST, "알 수 없는 변경 항목");
        assertRejected(() -> service.create(input(kim.getId(), List.of("TIME", "TIME"), null, null, null, null)),
                HttpStatus.BAD_REQUEST, "두 번");
        assertRejected(() -> service.create(new LabCarePlanChangeDto.Input(kim.getId(), CHANGED, "OTHER", " ",
                List.of("TIME"), "내용", null, null, null, null)), HttpStatus.BAD_REQUEST, "변경 사유 설명");
        assertRejected(() -> service.create(new LabCarePlanChangeDto.Input(kim.getId(), CHANGED, "OTHER", "사유",
                List.of("TIME"), null, null, null, null, null)), HttpStatus.BAD_REQUEST, "변경 내용");
        assertRejected(() -> service.create(new LabCarePlanChangeDto.Input(kim.getId(), CHANGED, "BAD", "사유",
                List.of("TIME"), "내용", null, null, null, null)), HttpStatus.BAD_REQUEST, "변경 사유");
        assertThat(changes.count()).isZero();

        LabCarePlanChangeDto.View all = service.create(input(kim.getId(), List.of("FREQUENCY", "CONTENT", "TIME"),
                null, null, null, null));
        assertThat(all.changeItems()).containsExactly("CONTENT", "TIME", "FREQUENCY");
    }

    // 보호자 설명·서명·공단 통보일은 변경일보다 앞설 수 없고 오늘 이후일 수 없다. 변경일도 미래 불가
    @Test
    void confirmDatesNotBeforeChange() {
        LocalDate before = CHANGED.minusDays(1);
        assertRejected(() -> service.create(input(kim.getId(), List.of("TIME"), null, before, null, null)),
                HttpStatus.BAD_REQUEST, "보호자 설명일");
        assertRejected(() -> service.create(input(kim.getId(), List.of("TIME"), null, null, before, null)),
                HttpStatus.BAD_REQUEST, "보호자 서명일");
        assertRejected(() -> service.create(input(kim.getId(), List.of("TIME"), null, null, null, before)),
                HttpStatus.BAD_REQUEST, "공단 통보일");
        LocalDate future = LabValidation.seoulToday().plusDays(1);
        assertRejected(() -> service.create(input(kim.getId(), List.of("TIME"), null, null, future, null)),
                HttpStatus.BAD_REQUEST, "보호자 서명일");
        assertRejected(() -> service.create(new LabCarePlanChangeDto.Input(kim.getId(), future, "OTHER", "사유",
                List.of("TIME"), "내용", null, null, null, null)), HttpStatus.BAD_REQUEST, "변경일");
        assertThat(changes.count()).isZero();

        LabCarePlanChangeDto.View ok = service.create(input(kim.getId(), List.of("TIME"), null, CHANGED,
                CHANGED.plusDays(1), CHANGED.plusDays(3)));
        assertThat(ok.guardianSignedOn()).isEqualTo(CHANGED.plusDays(1));
        assertThat(ok.notifiedOn()).isEqualTo(CHANGED.plusDays(3));
    }
}
