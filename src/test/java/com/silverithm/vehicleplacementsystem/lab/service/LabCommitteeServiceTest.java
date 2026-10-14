package com.silverithm.vehicleplacementsystem.lab.service;

import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertEncryptedAtRest;
import static com.silverithm.vehicleplacementsystem.lab.service.LabTestAsserts.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCommitteeDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCommitteeMeetingRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCommitteeMemberRepository;
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
class LabCommitteeServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Autowired private LabCommitteeService service;
    @Autowired private LabCommitteeMemberRepository members;
    @Autowired private LabCommitteeMeetingRepository meetings;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LabTestAccess access;

    private Company forest;

    @BeforeEach
    void setUp() {
        forest = LabTestFixtures.company(em, "숲속");
        access.companyId = forest.getId();
    }

    private LabCommitteeDto.MemberInput member(String name, LocalDate start, LocalDate end) {
        return new LabCommitteeDto.MemberInput(name, "GUARDIAN", start, end, true, "보호자 대표");
    }

    private LabCommitteeDto.MeetingInput meeting(LocalDate heldOn, List<Long> ids) {
        return new LabCommitteeDto.MeetingInput(heldOn, "REGULAR", ids, "1분기 운영 보고", "논의 내용 전문",
                List.of("프로그램 확대"), null, "김센터", false);
    }

    // 임기는 시작 이후 3년까지 — 딱 3년은 통과, 하루라도 넘으면 계약 메시지로 400
    @Test
    void termOverThreeYearsRejected() {
        assertRejected(() -> service.createMember(member("홍길동", START, LocalDate.of(2029, 1, 2))),
                HttpStatus.BAD_REQUEST, "운영위원 임기는 3년을 넘을 수 없습니다");
        assertRejected(() -> service.createMember(member("홍길동", START, LocalDate.of(2031, 1, 1))),
                HttpStatus.BAD_REQUEST, "운영위원 임기는 3년을 넘을 수 없습니다");
        assertRejected(() -> service.createMember(member("홍길동", START, START)), HttpStatus.BAD_REQUEST,
                "시작일보다 뒤");
        assertRejected(() -> service.createMember(member("홍길동", START, START.minusDays(1))),
                HttpStatus.BAD_REQUEST, "시작일보다 뒤");
        assertRejected(() -> service.createMember(member("홍길동", null, START)), HttpStatus.BAD_REQUEST, "임기 시작일");
        assertThat(members.count()).isZero();

        assertThat(service.createMember(member("홍길동", START, LocalDate.of(2029, 1, 1))).termEnd())
                .isEqualTo(LocalDate.of(2029, 1, 1));
        // 윤일 시작도 3년 뒤 같은 날까지
        assertThat(service.createMember(member("윤일", LocalDate.of(2024, 2, 29), LocalDate.of(2027, 2, 28))).id())
                .isNotNull();
        assertRejected(() -> service.createMember(member("윤일", LocalDate.of(2024, 2, 29), LocalDate.of(2027, 3, 2))),
                HttpStatus.BAD_REQUEST, "3년을 넘을 수 없습니다");
        LabCommitteeDto.MemberView saved = service.createMember(member("임기", START, LocalDate.of(2026, 12, 31)));
        assertRejected(() -> service.updateMember(saved.id(), member("임기", START, LocalDate.of(2029, 1, 2))),
                HttpStatus.BAD_REQUEST, "3년을 넘을 수 없습니다");

        // 역할·이름 검증, 활성 기본값
        assertRejected(() -> service.createMember(new LabCommitteeDto.MemberInput("위원", "BOSS", START,
                START.plusYears(1), true, null)), HttpStatus.BAD_REQUEST, "역할");
        assertRejected(() -> service.createMember(member(" ", START, START.plusYears(1))), HttpStatus.BAD_REQUEST,
                "위원 이름");
        assertRejected(() -> service.createMember(member("가".repeat(51), START, START.plusYears(1))),
                HttpStatus.BAD_REQUEST, "50자");
        assertThat(service.createMember(new LabCommitteeDto.MemberInput("신규", "CHAIR", START, START.plusYears(1),
                null, null)).active()).isTrue();
    }

    // 위원 이름은 저장된 열이 암호문이고, 읽으면 평문으로 돌아온다
    @Test
    void memberNameEncryptedAtRest() {
        LabCommitteeDto.MemberView saved = service.createMember(member("홍길동", START, LocalDate.of(2028, 12, 31)));
        em.flush();
        em.clear();
        assertEncryptedAtRest(jdbc, "lab_committee_members", "name", "홍길동");
        assertThat(service.listMembers()).extracting(LabCommitteeDto.MemberView::name).containsExactly("홍길동");
        assertThat(service.listMembers().get(0).id()).isEqualTo(saved.id());
        // 역할·임기는 평문 열
        assertThat(jdbc.queryForObject("SELECT role FROM lab_committee_members", String.class)).isEqualTo("GUARDIAN");
    }

    // 참석 위원은 같은 기관 운영위원만 — 다른 기관·없는 id가 하나라도 있으면 통째로 거절, 30명 초과는 400
    @Test
    void attendeesMustBeSameCompanyMembers() {
        LabCommitteeDto.MemberView mine = service.createMember(member("홍길동", START, START.plusYears(2)));
        Company other = LabTestFixtures.company(em, "다른기관");
        access.companyId = other.getId();
        LabCommitteeDto.MemberView theirs = service.createMember(member("남위원", START, START.plusYears(2)));
        access.companyId = forest.getId();

        assertRejected(() -> service.createMeeting(meeting(START, List.of(mine.id(), theirs.id()))),
                HttpStatus.NOT_FOUND, "운영위원을 찾을 수 없습니다");
        assertRejected(() -> service.createMeeting(meeting(START, List.of(999_999L))), HttpStatus.NOT_FOUND, null);
        assertThat(meetings.count()).isZero();
        LabCommitteeDto.MeetingView ok = service.createMeeting(meeting(START, List.of(mine.id())));
        assertRejected(() -> service.updateMeeting(ok.id(), meeting(START, List.of(theirs.id()))),
                HttpStatus.NOT_FOUND, null);

        List<Long> thirtyOne = new ArrayList<>();
        for (long i = 1; i <= 31; i++) {
            thirtyOne.add(i);
        }
        assertRejected(() -> service.createMeeting(meeting(START, thirtyOne)), HttpStatus.BAD_REQUEST, "30명");
        List<Long> nullElement = new ArrayList<>();
        nullElement.add(null);
        assertRejected(() -> service.createMeeting(meeting(START, nullElement)), HttpStatus.BAD_REQUEST, "올바르지");

        // 다른 기관은 이 회의를 고치거나 지울 수 없다
        access.companyId = other.getId();
        assertRejected(() -> service.updateMeeting(ok.id(), meeting(START, List.of())), HttpStatus.NOT_FOUND,
                "회의 기록을 찾을 수 없습니다");
        assertRejected(() -> service.deleteMember(mine.id()), HttpStatus.NOT_FOUND, "운영위원을 찾을 수 없습니다");
        assertThat(service.listMeetings("2026")).isEmpty();
        access.companyId = forest.getId();

        // 안건·결정 사항 규칙
        assertRejected(() -> service.createMeeting(new LabCommitteeDto.MeetingInput(START, "REGULAR", null, " ",
                null, null, null, null, null)), HttpStatus.BAD_REQUEST, "안건");
        assertRejected(() -> service.createMeeting(new LabCommitteeDto.MeetingInput(START, "ANNUAL", null, "안건",
                null, null, null, null, null)), HttpStatus.BAD_REQUEST, "회의 종류");
        List<String> twentyOne = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOne.add("결정 " + i);
        }
        assertRejected(() -> service.createMeeting(new LabCommitteeDto.MeetingInput(START, "SPECIAL", null, "안건",
                null, twentyOne, null, null, null)), HttpStatus.BAD_REQUEST, "20개");
        assertRejected(() -> service.createMeeting(new LabCommitteeDto.MeetingInput(START, "SPECIAL", null, "안건",
                null, List.of("가".repeat(201)), null, null, null)), HttpStatus.BAD_REQUEST, "200자");
        assertRejected(() -> service.createMeeting(new LabCommitteeDto.MeetingInput(START, "SPECIAL", null, "안건",
                "가".repeat(5001), null, null, null, null)), HttpStatus.BAD_REQUEST, "5000자");
        List<String> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            twenty.add("가".repeat(200));
        }
        LabCommitteeDto.MeetingView full = service.createMeeting(new LabCommitteeDto.MeetingInput(START, "SPECIAL",
                null, "안건", "가".repeat(5000), twenty, null, null, null));
        assertThat(full.decisions()).hasSize(20);
        assertThat(full.attendeeIds()).isEmpty();
    }

    // 응답에 참석 위원 이름이 입력 순서대로 채워지고, 회의록은 암호화 열이며, 지워진 위원은 이름 없이 남지 않는다
    @Test
    void attendeeNamesFilled() {
        LabCommitteeDto.MemberView hong = service.createMember(member("홍길동", START, START.plusYears(2)));
        LabCommitteeDto.MemberView kang = service.createMember(member("강감찬", START, START.plusYears(2)));

        LabCommitteeDto.MeetingView created = service.createMeeting(new LabCommitteeDto.MeetingInput(
                LocalDate.of(2026, 3, 10), "REGULAR", List.of(kang.id(), hong.id(), kang.id()), "1분기 보고",
                "회의록 본문", List.of("프로그램 확대", "  ", "예산 승인"), LocalDate.of(2026, 6, 10), "김센터", true));
        assertThat(created.attendeeIds()).containsExactly(kang.id(), hong.id());
        assertThat(created.attendeeNames()).containsExactly("강감찬", "홍길동");
        assertThat(created.decisions()).containsExactly("프로그램 확대", "예산 승인");
        assertThat(created.aiAssisted()).isTrue();
        assertThat(created.nextOn()).isEqualTo(LocalDate.of(2026, 6, 10));

        em.flush();
        em.clear();
        assertEncryptedAtRest(jdbc, "lab_committee_meetings", "minutes", "회의록 본문");
        LabCommitteeDto.MeetingView read = service.listMeetings("2026").get(0);
        assertThat(read.minutes()).isEqualTo("회의록 본문");
        assertThat(read.attendeeNames()).containsExactly("강감찬", "홍길동");
        assertThat(read.writerName()).isEqualTo("김센터");

        LabCommitteeDto.MeetingView updated = service.updateMeeting(created.id(),
                meeting(LocalDate.of(2026, 3, 10), List.of(hong.id())));
        assertThat(updated.attendeeNames()).containsExactly("홍길동");

        service.deleteMember(hong.id());
        LabCommitteeDto.MeetingView afterDelete = service.listMeetings("2026").get(0);
        assertThat(afterDelete.attendeeIds()).isEmpty();
        assertThat(afterDelete.attendeeNames()).isEmpty();
    }

    // 연도 필터 — 1/1~12/31 양 끝 포함, 최근 회의 먼저, 형식이 틀리면 400
    @Test
    void yearFilter() {
        service.createMeeting(meeting(LocalDate.of(2025, 12, 31), null));
        LabCommitteeDto.MeetingView jan = service.createMeeting(meeting(LocalDate.of(2026, 1, 1), null));
        LabCommitteeDto.MeetingView dec = service.createMeeting(meeting(LocalDate.of(2026, 12, 31), null));
        service.createMeeting(meeting(LocalDate.of(2027, 1, 1), null));

        assertThat(service.listMeetings("2026")).extracting(LabCommitteeDto.MeetingView::id)
                .containsExactly(dec.id(), jan.id());
        assertThat(service.listMeetings("2028")).isEmpty();
        assertRejected(() -> service.listMeetings("26"), HttpStatus.BAD_REQUEST, "YYYY");
        assertRejected(() -> service.listMeetings(null), HttpStatus.BAD_REQUEST, "YYYY");
        assertRejected(() -> service.listMeetings("1999"), HttpStatus.BAD_REQUEST, "2000~2100");

        service.deleteMeeting(jan.id());
        assertThat(service.listMeetings("2026")).extracting(LabCommitteeDto.MeetingView::id).containsExactly(dec.id());
    }
}
