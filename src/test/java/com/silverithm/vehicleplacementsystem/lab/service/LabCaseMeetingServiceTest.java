package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCaseMeetingDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCaseMeetingRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@LabJpaTest
class LabCaseMeetingServiceTest {

    private static final LocalDate HELD = LocalDate.of(2026, 3, 10);

    @Autowired private LabCaseMeetingService service;
    @Autowired private LabCaseMeetingRepository meetings;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;
    private Member director;
    private Member worker;
    private Member nurse;
    private Elderly kim;
    private Elderly lee;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        director = LabTestFixtures.member(em, forest, "원장", Member.Role.ADMIN, Member.MemberStatus.ACTIVE);
        worker = LabTestFixtures.member(em, forest, "사회복지사", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        nurse = LabTestFixtures.member(em, forest, "간호조무사", Member.Role.CAREGIVER, Member.MemberStatus.ACTIVE);
        kim = LabTestFixtures.elder(em, forest, "김어르신");
        lee = LabTestFixtures.elder(em, forest, "이어르신");
        access.companyId = forest.getId();
    }

    private LabCaseMeetingDto.TargetInput target(Long elderlyId) {
        return new LabCaseMeetingDto.TargetInput(elderlyId, "낙상 위험이 커짐", "보행 보조 운동과 환경 정비", worker.getId(),
                null, null, null, null, null, null, null);
    }

    private LabCaseMeetingDto.TargetInput target(Long elderlyId, LocalDate reflectedOn, LocalDate evaluatedOn,
                                                 String evaluator, Boolean resolved, String nextAction) {
        return new LabCaseMeetingDto.TargetInput(elderlyId, "낙상 위험이 커짐", "보행 보조 운동과 환경 정비", worker.getId(),
                reflectedOn, reflectedOn == null ? null : "CARE_PLAN", "돌봄계획에 반영", evaluatedOn, evaluator, resolved,
                nextAction);
    }

    private LabCaseMeetingDto.Input input(List<Long> attendees, List<LabCaseMeetingDto.OpinionInput> opinions,
                                          List<LabCaseMeetingDto.TargetInput> targets) {
        return new LabCaseMeetingDto.Input(HELD, attendees, opinions, targets, "회의 내용 요약", false);
    }

    private LabCaseMeetingDto.Input simple(LabCaseMeetingDto.TargetInput... targets) {
        return input(List.of(director.getId(), worker.getId(), nurse.getId()), List.of(), List.of(targets));
    }

    // 참석자·담당·대상 어르신은 모두 같은 기관 — 하나라도 다른 기관이면 통째로 404, 저장도 없다. 이름은 서버가 채운다
    @Test
    void attendeesAndTargetsMustBelongToCompany() {
        Company other = LabTestFixtures.company(em, "다른기관");
        Member outsider = LabTestFixtures.member(em, other, "외부직원", Member.Role.CAREGIVER,
                Member.MemberStatus.ACTIVE);
        Elderly outElder = LabTestFixtures.elder(em, other, "외부어르신");

        assertRejected(() -> service.create(input(List.of(director.getId(), outsider.getId(), nurse.getId()),
                List.of(), List.of(target(kim.getId())))), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertRejected(() -> service.create(simple(target(outElder.getId()))), HttpStatus.NOT_FOUND,
                "어르신을 찾을 수 없습니다");
        assertRejected(() -> service.create(simple(target(999_999L))), HttpStatus.NOT_FOUND, "어르신을 찾을 수 없습니다");
        LabCaseMeetingDto.TargetInput foreignOwner = new LabCaseMeetingDto.TargetInput(kim.getId(), "사유", "계획",
                outsider.getId(), null, null, null, null, null, null, null);
        assertRejected(() -> service.create(simple(foreignOwner)), HttpStatus.NOT_FOUND, "직원을 찾을 수 없습니다");
        assertThat(meetings.count()).isZero();

        LabCaseMeetingDto.View saved = service.create(simple(target(kim.getId()), target(lee.getId())));
        assertThat(saved.attendees()).extracting(LabCaseMeetingDto.Attendee::memberName)
                .containsExactly("원장", "사회복지사", "간호조무사");
        assertThat(saved.targets()).extracting(LabCaseMeetingDto.Target::elderlyName)
                .containsExactly("김어르신", "이어르신");
        assertThat(saved.targets().get(0).ownerName()).isEqualTo("사회복지사");
        assertThat(saved.writerName()).isEqualTo("숲속 관리자");
        assertThat(saved.minutes()).isEqualTo("회의 내용 요약");
        assertEncryptedAtRest(jdbc, "lab_case_meetings", "minutes", "회의 내용 요약");

        // 다른 기관은 이 회의를 보지도 고치지도 지우지도 못한다
        access.companyId = other.getId();
        assertThat(service.list("2026")).isEmpty();
        assertRejected(() -> service.update(saved.id(), simple(target(outElder.getId()))), HttpStatus.NOT_FOUND,
                "찾을 수 없습니다");
        assertRejected(() -> service.delete(saved.id()), HttpStatus.NOT_FOUND, "찾을 수 없습니다");
        access.companyId = forest.getId();
        assertThat(meetings.count()).isEqualTo(1);

        // 입력 모양: 참석자 0명·31명·중복, 대상 0명·11명·중복, 미래 회의일
        assertRejected(() -> service.create(input(List.of(), List.of(), List.of(target(kim.getId())))),
                HttpStatus.BAD_REQUEST, "참석자");
        assertRejected(() -> service.create(input(List.of(director.getId(), director.getId()), List.of(),
                List.of(target(kim.getId())))), HttpStatus.BAD_REQUEST, "참석자");
        assertRejected(() -> service.create(input(java.util.stream.LongStream.rangeClosed(1, 31).boxed().toList(),
                List.of(), List.of(target(kim.getId())))), HttpStatus.BAD_REQUEST, "참석자");
        assertRejected(() -> service.create(input(List.of(director.getId()), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "대상");
        assertRejected(() -> service.create(simple(target(kim.getId()), target(kim.getId()))),
                HttpStatus.BAD_REQUEST, "두 번");
        assertRejected(() -> service.create(input(List.of(director.getId()), List.of(), java.util.stream.LongStream
                .rangeClosed(1, 11).mapToObj(i -> target(kim.getId())).toList())), HttpStatus.BAD_REQUEST, "최대 10명");
        assertRejected(() -> service.create(new LabCaseMeetingDto.Input(LabValidation.seoulToday().plusDays(1),
                List.of(director.getId()), List.of(), List.of(target(kim.getId())), null, false)),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.create(simple(new LabCaseMeetingDto.TargetInput(kim.getId(), " ", "계획", null,
                null, null, null, null, null, null, null))), HttpStatus.BAD_REQUEST, "선정 사유");
        assertRejected(() -> service.create(simple(new LabCaseMeetingDto.TargetInput(kim.getId(), "사유", null, null,
                null, null, null, null, null, null, null))), HttpStatus.BAD_REQUEST, "사례관리 계획");
        assertThat(meetings.count()).isEqualTo(1);
    }

    // 의견은 참석자의 것만, 참석자마다 하나. 의견 없음(null)도 기록되고 이름이 채워진다
    @Test
    void opinionOnlyForAttendees() {
        List<Long> attendees = List.of(director.getId(), worker.getId());
        // 같은 기관 직원이어도 참석자가 아니면 의견을 낼 수 없다
        assertRejected(() -> service.create(input(attendees,
                List.of(new LabCaseMeetingDto.OpinionInput(nurse.getId(), "의견")), List.of(target(kim.getId())))),
                HttpStatus.BAD_REQUEST, "참석자만");
        assertRejected(() -> service.create(input(attendees, List.of(
                new LabCaseMeetingDto.OpinionInput(worker.getId(), "하나"),
                new LabCaseMeetingDto.OpinionInput(worker.getId(), "둘")), List.of(target(kim.getId())))),
                HttpStatus.BAD_REQUEST, "하나만");
        assertRejected(() -> service.create(input(attendees,
                List.of(new LabCaseMeetingDto.OpinionInput(worker.getId(), "가".repeat(301))),
                List.of(target(kim.getId())))), HttpStatus.BAD_REQUEST, "의견");
        assertRejected(() -> service.create(input(attendees,
                List.of(new LabCaseMeetingDto.OpinionInput(null, "의견")), List.of(target(kim.getId())))),
                HttpStatus.BAD_REQUEST, "참석자");
        assertThat(meetings.count()).isZero();

        LabCaseMeetingDto.View saved = service.create(input(attendees, List.of(
                new LabCaseMeetingDto.OpinionInput(worker.getId(), "보행 훈련을 늘리자"),
                new LabCaseMeetingDto.OpinionInput(director.getId(), "  ")), List.of(target(kim.getId()))));
        assertThat(saved.opinions()).extracting(LabCaseMeetingDto.Opinion::memberName, LabCaseMeetingDto.Opinion::opinion)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("사회복지사", "보행 훈련을 늘리자"),
                        org.assertj.core.groups.Tuple.tuple("원장", null));

        // 수정으로 참석자를 빼면 그 사람의 의견도 같이 빠져야 한다 — 남기려 하면 거절
        assertRejected(() -> service.update(saved.id(), input(List.of(director.getId()),
                List.of(new LabCaseMeetingDto.OpinionInput(worker.getId(), "보행 훈련을 늘리자")),
                List.of(target(kim.getId())))), HttpStatus.BAD_REQUEST, "참석자만");
        LabCaseMeetingDto.View updated = service.update(saved.id(), input(List.of(director.getId()), List.of(),
                List.of(target(kim.getId()))));
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.opinions()).isEmpty();
        assertThat(updated.attendees()).hasSize(1);
        assertThat(meetings.count()).isEqualTo(1);
    }

    // 반영일 ≥ 회의일, 평가일 ≥ 반영일, 반영 없이 평가 불가, 반영일이 있으면 방식 필수, 미래 날짜 400
    @Test
    void reflectAndEvaluateDateOrder() {
        LocalDate day = HELD.plusDays(10);
        assertRejected(() -> service.create(simple(target(kim.getId(), HELD.minusDays(1), null, null, null, null))),
                HttpStatus.BAD_REQUEST, "반영일");
        assertRejected(() -> service.create(simple(target(kim.getId(), day, day.minusDays(1), "원장", true, null))),
                HttpStatus.BAD_REQUEST, "평가일");
        assertRejected(() -> service.create(simple(target(kim.getId(), null, day, "원장", true, null))),
                HttpStatus.BAD_REQUEST, "반영일을 먼저");
        assertRejected(() -> service.create(simple(new LabCaseMeetingDto.TargetInput(kim.getId(), "사유", "계획", null,
                day, null, null, null, null, null, null))), HttpStatus.BAD_REQUEST, "반영 방식");
        assertRejected(() -> service.create(simple(new LabCaseMeetingDto.TargetInput(kim.getId(), "사유", "계획", null,
                day, "MAGIC", null, null, null, null, null))), HttpStatus.BAD_REQUEST, "반영 방식");
        LocalDate future = LabValidation.seoulToday().plusDays(1);
        assertRejected(() -> service.create(simple(target(kim.getId(), future, null, null, null, null))),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertRejected(() -> service.create(simple(target(kim.getId(), day, future, "원장", true, null))),
                HttpStatus.BAD_REQUEST, "오늘 이후");
        assertThat(meetings.count()).isZero();

        // 회의 당일 반영·같은 날 평가는 통과
        LabCaseMeetingDto.View ok = service.create(simple(target(kim.getId(), HELD, HELD, "원장", true, null)));
        LabCaseMeetingDto.Target t = ok.targets().get(0);
        assertThat(t.reflectedOn()).isEqualTo(HELD);
        assertThat(t.evaluatedOn()).isEqualTo(HELD);
        assertThat(t.reflectMethod()).isEqualTo("CARE_PLAN");
        assertThat(t.evaluatorName()).isEqualTo("원장");
        assertThat(t.resolved()).isTrue();
        // 반영만 한 대상도 저장된다(평가는 아직)
        LabCaseMeetingDto.View reflectedOnly = service.create(simple(target(lee.getId(), day, null, null, null, null)));
        assertThat(reflectedOnly.targets().get(0).evaluatedOn()).isNull();
        assertThat(reflectedOnly.targets().get(0).resolved()).isNull();
        // 목록은 회의일 최신순, 연도 밖은 나오지 않는다
        assertThat(service.list("2026")).hasSize(2);
        assertThat(service.list("2025")).isEmpty();
    }

    // 평가했으면 평가자·해결 여부가 필수, 해결 못 했으면 추가 조치가 필수
    @Test
    void unresolvedRequiresNextAction() {
        LocalDate reflected = HELD.plusDays(3);
        LocalDate evaluated = HELD.plusDays(20);
        assertRejected(() -> service.create(simple(target(kim.getId(), reflected, evaluated, null, true, null))),
                HttpStatus.BAD_REQUEST, "평가자");
        assertRejected(() -> service.create(simple(target(kim.getId(), reflected, evaluated, "  ", true, null))),
                HttpStatus.BAD_REQUEST, "평가자");
        assertRejected(() -> service.create(simple(target(kim.getId(), reflected, evaluated, "원장", null, null))),
                HttpStatus.BAD_REQUEST, "해결 여부");
        assertRejected(() -> service.create(simple(target(kim.getId(), reflected, evaluated, "원장", false, null))),
                HttpStatus.BAD_REQUEST, "추가 조치");
        assertRejected(() -> service.create(simple(target(kim.getId(), reflected, evaluated, "원장", false, "  "))),
                HttpStatus.BAD_REQUEST, "추가 조치");
        assertThat(meetings.count()).isZero();

        LabCaseMeetingDto.View saved = service.create(simple(
                target(kim.getId(), reflected, evaluated, "원장", false, "한 달 더 지켜보고 재평가")));
        assertThat(saved.targets().get(0).resolved()).isFalse();
        assertThat(saved.targets().get(0).nextAction()).isEqualTo("한 달 더 지켜보고 재평가");
        // 해결했으면 추가 조치 없이 저장
        assertThat(service.update(saved.id(), simple(target(kim.getId(), reflected, evaluated, "원장", true, null)))
                .targets().get(0).nextAction()).isNull();
        // 거절된 수정은 기존 값을 바꾸지 않는다
        assertRejected(() -> service.update(saved.id(), simple(target(kim.getId(), reflected, evaluated, "원장", false,
                null))), HttpStatus.BAD_REQUEST, "추가 조치");
        assertThat(service.list("2026").get(0).targets().get(0).resolved()).isTrue();
    }
}
