package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCommitteeDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMeeting;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMember;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCommitteeMeetingRepository;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCommitteeMemberRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영위원회(노인복지법 시행규칙 별표 10·사회복지사업법 제36조) — 위원 명단과 회의록.
 *
 * <p>위원 임기는 시작 이후 3년을 넘을 수 없다. 회의의 참석 위원은 같은 기관 운영위원 id여야 하고 한 명이라도
 * 아니면 통째로 거절한다(404). 응답은 서버가 참석자 이름을 채운다 — 지금도 있는 위원만 id와 이름이 짝으로 나가
 * 위원이 삭제돼도 빈 이름이 목록에 뜨지 않는다. 위원 이름·회의록은 암호화한다.
 */
@Service
public class LabCommitteeService {

    static final Set<String> ROLES = Set.of("CHAIR", "FACILITY_HEAD", "STAFF", "USER", "GUARDIAN", "EXPERT",
            "LOCAL", "PUBLIC_OFFICIAL", "OTHER");
    static final Set<String> MEETING_KINDS = Set.of("REGULAR", "SPECIAL");
    static final int MAX_ATTENDEES = 30;
    static final int MAX_DECISIONS = 20;
    private static final int DECISION = 200;
    private static final int AGENDA = 2000;
    private static final int MINUTES = 5000;

    private final LabAccessService access;
    private final LabCommitteeMemberRepository members;
    private final LabCommitteeMeetingRepository meetings;

    public LabCommitteeService(LabAccessService access, LabCommitteeMemberRepository members,
                               LabCommitteeMeetingRepository meetings) {
        this.access = access;
        this.members = members;
        this.meetings = meetings;
    }

    // ---- 위원 ----

    @Transactional(readOnly = true)
    public List<LabCommitteeDto.MemberView> listMembers() {
        Long companyId = access.requireCompanyId();
        return members.findByCompanyIdOrderByIdAsc(companyId).stream()
                .map(LabCommitteeService::toView).toList();
    }

    @Transactional
    public LabCommitteeDto.MemberView createMember(LabCommitteeDto.MemberInput input) {
        Long companyId = access.requireCompanyId();
        LabCommitteeMember member = new LabCommitteeMember(companyId);
        applyMember(member, input);
        return toView(members.saveAndFlush(member));
    }

    @Transactional
    public LabCommitteeDto.MemberView updateMember(Long id, LabCommitteeDto.MemberInput input) {
        Long companyId = access.requireCompanyId();
        LabCommitteeMember member = members.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabCommitteeService::memberNotFound);
        applyMember(member, input);
        return toView(members.saveAndFlush(member));
    }

    @Transactional
    public void deleteMember(Long id) {
        Long companyId = access.requireCompanyId();
        members.delete(members.findByIdAndCompanyId(id, companyId).orElseThrow(LabCommitteeService::memberNotFound));
    }

    private void applyMember(LabCommitteeMember member, LabCommitteeDto.MemberInput input) {
        if (input == null) {
            throw LabValidation.bad("운영위원 내용을 보내 주세요");
        }
        String name = LabValidation.requiredText(input.name(), 50, "위원 이름");
        String role = LabValidation.oneOf(input.role(), ROLES, "역할");
        LocalDate start = LabValidation.plausibleDate(input.termStart(), "임기 시작일");
        LocalDate end = LabValidation.plausibleDate(input.termEnd(), "임기 종료일");
        if (!end.isAfter(start)) {
            throw LabValidation.bad("임기 종료일은 시작일보다 뒤여야 합니다");
        }
        if (end.isAfter(start.plusYears(3))) {
            throw LabValidation.bad("운영위원 임기는 3년을 넘을 수 없습니다");
        }
        String note = LabValidation.text(input.note(), 200, "메모");
        // 활성 여부를 안 보내면 새로 위촉한 위원으로 본다
        boolean active = input.active() == null || input.active();
        member.fill(name, role, start, end, active, note);
    }

    // ---- 회의 ----

    @Transactional(readOnly = true)
    public List<LabCommitteeDto.MeetingView> listMeetings(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabCommitteeMeeting> rows = meetings.findByCompanyIdAndHeldOnBetweenOrderByHeldOnDescIdDesc(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        // 참석 위원 이름은 그 해 모든 회의의 id를 모아 한 번에 읽는다
        Set<Long> allIds = new LinkedHashSet<>();
        for (LabCommitteeMeeting m : rows) {
            allIds.addAll(LabJson.readIds(m.getAttendeeIds()));
        }
        Map<Long, String> names = namesOf(allIds, companyId);
        return rows.stream().map(m -> toView(m, names)).toList();
    }

    @Transactional
    public LabCommitteeDto.MeetingView createMeeting(LabCommitteeDto.MeetingInput input) {
        Long companyId = access.requireCompanyId();
        LabCommitteeMeeting meeting = new LabCommitteeMeeting(companyId);
        Map<Long, String> names = applyMeeting(meeting, input, companyId);
        return toView(meetings.saveAndFlush(meeting), names);
    }

    @Transactional
    public LabCommitteeDto.MeetingView updateMeeting(Long id, LabCommitteeDto.MeetingInput input) {
        Long companyId = access.requireCompanyId();
        LabCommitteeMeeting meeting = meetings.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabCommitteeService::meetingNotFound);
        Map<Long, String> names = applyMeeting(meeting, input, companyId);
        return toView(meetings.saveAndFlush(meeting), names);
    }

    @Transactional
    public void deleteMeeting(Long id) {
        Long companyId = access.requireCompanyId();
        meetings.delete(meetings.findByIdAndCompanyId(id, companyId)
                .orElseThrow(LabCommitteeService::meetingNotFound));
    }

    private Map<Long, String> applyMeeting(LabCommitteeMeeting meeting, LabCommitteeDto.MeetingInput input,
                                           Long companyId) {
        if (input == null) {
            throw LabValidation.bad("회의 내용을 보내 주세요");
        }
        LocalDate heldOn = LabValidation.plausibleDate(input.heldOn(), "회의일");
        String kind = LabValidation.oneOf(input.kind(), MEETING_KINDS, "회의 종류");
        List<Long> ids = LabValidation.idList(input.attendeeIds(), MAX_ATTENDEES, "참석 위원");
        Map<Long, String> names = namesOf(ids, companyId);
        for (Long id : ids) {
            if (!names.containsKey(id)) {
                throw memberNotFound();
            }
        }
        String agenda = LabValidation.requiredText(input.agenda(), AGENDA, "안건");
        String minutes = LabValidation.text(input.minutes(), MINUTES, "회의록");
        String decisions = LabJson.write(decisions(input.decisions()));
        LocalDate nextOn = input.nextOn() == null ? null : LabValidation.plausibleDate(input.nextOn(), "다음 회의일");
        String writer = LabValidation.text(input.writerName(), 50, "작성자 이름");
        meeting.fill(heldOn, kind, LabJson.write(ids), agenda, minutes, decisions, nextOn, writer,
                Boolean.TRUE.equals(input.aiAssisted()));
        return names;
    }

    /** 결정 사항 — 20개 이하·각 200자 이하. 빈 칸은 버린다 */
    private static List<String> decisions(List<String> input) {
        if (input == null) {
            return List.of();
        }
        if (input.size() > MAX_DECISIONS) {
            throw LabValidation.bad("결정 사항은 최대 " + MAX_DECISIONS + "개까지 입력할 수 있습니다");
        }
        List<String> clean = new ArrayList<>();
        for (String text : input) {
            String d = LabValidation.text(text, DECISION, "결정 사항");
            if (d != null) {
                clean.add(d);
            }
        }
        return clean;
    }

    /** 이 기관 위원의 이름(입력 순서 유지, 없는 id·다른 기관 id는 빠진다) */
    private Map<Long, String> namesOf(java.util.Collection<Long> ids, Long companyId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> found = members.findByCompanyIdAndIdIn(companyId, ids).stream()
                .collect(Collectors.toMap(LabCommitteeMember::getId, LabCommitteeMember::getName));
        Map<Long, String> ordered = new LinkedHashMap<>();
        for (Long id : ids) {
            String name = found.get(id);
            if (name != null) {
                ordered.put(id, name);
            }
        }
        return ordered;
    }

    private static LabCommitteeDto.MemberView toView(LabCommitteeMember m) {
        return new LabCommitteeDto.MemberView(m.getId(), m.getName(), m.getRole(), m.getTermStart(),
                m.getTermEnd(), m.isActive(), m.getNote(), m.getUpdatedAt());
    }

    private static LabCommitteeDto.MeetingView toView(LabCommitteeMeeting m, Map<Long, String> names) {
        List<Long> ids = new ArrayList<>();
        List<String> attendeeNames = new ArrayList<>();
        for (Long id : LabJson.readIds(m.getAttendeeIds())) {
            String name = names.get(id);
            if (name != null) {
                ids.add(id);
                attendeeNames.add(name);
            }
        }
        return new LabCommitteeDto.MeetingView(m.getId(), m.getHeldOn(), m.getKind(), ids, attendeeNames,
                m.getAgenda(), m.getMinutes(), LabJson.readStrings(m.getDecisions()), m.getNextOn(),
                m.getWriterName(), m.isAiAssisted(), m.getUpdatedAt());
    }

    private static CustomException memberNotFound() {
        return new CustomException("운영위원을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }

    private static CustomException meetingNotFound() {
        return new CustomException("회의 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
