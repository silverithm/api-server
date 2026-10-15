package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCaseMeetingDto;
import com.silverithm.vehicleplacementsystem.lab.entity.LabCaseMeeting;
import com.silverithm.vehicleplacementsystem.lab.repository.LabCaseMeetingRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사례관리 회의 — 참석자(직원)·참석자별 의견·대상 어르신과 대상마다의 반영·평가 기록.
 *
 * <p>참석자·담당·대상 id는 클라이언트가 보내는 값이라 모두 요청자 기관 소속인지 확인하고, 하나라도 아니면
 * 존재 여부를 알려 주지 않도록 "없음"과 같은 404로 통째로 거절한다. 의견은 참석자의 것만 받는다(400).
 * 반영일은 회의일 이상, 평가일은 반영일 이상이며 모두 오늘 이후일 수 없다. 평가했으면 평가자와 해결 여부가
 * 있어야 하고, 해결하지 못했으면 추가 조치가 있어야 한다. 응답의 이름은 서버가 채운다. 기한 판정은 웹이 한다.
 */
@Service
public class LabCaseMeetingService {

    static final Set<String> REFLECT_METHODS = Set.of("CARE_PLAN", "CARE_RECORD", "RESOURCE", "INTERNAL");
    static final int MAX_ATTENDEES = 30;
    static final int MAX_TARGETS = 10;
    private static final int OPINION = 300;
    private static final int REASON = 300;
    private static final int PLAN = 500;
    private static final int REFLECT_NOTE = 300;
    private static final int EVALUATOR = 50;
    private static final int NEXT_ACTION = 300;
    private static final int MINUTES = 3000;

    private final LabAccessService access;
    private final LabStaffService staff;
    private final LabElderDirectory elders;
    private final LabCaseMeetingRepository meetings;

    public LabCaseMeetingService(LabAccessService access, LabStaffService staff, LabElderDirectory elders,
                                 LabCaseMeetingRepository meetings) {
        this.access = access;
        this.staff = staff;
        this.elders = elders;
        this.meetings = meetings;
    }

    /** JSON 열에 저장하는 모양 — 날짜는 ISO 문자열로 둔다(저장용 매퍼에 날짜 모듈이 없다) */
    record StoredOpinion(Long memberId, String opinion) {
    }

    record StoredTarget(Long elderlyId, String reason, String plan, Long ownerMemberId, String reflectedOn,
                        String reflectMethod, String reflectNote, String evaluatedOn, String evaluatorName,
                        Boolean resolved, String nextAction) {
    }

    @Transactional(readOnly = true)
    public List<LabCaseMeetingDto.View> list(String year) {
        Long companyId = access.requireCompanyId();
        int y = LabValidation.year(year, "연도");
        List<LabCaseMeeting> rows = meetings.findByCompanyIdAndHeldOnBetweenOrderByHeldOnDescIdDesc(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        // 이름은 그 해 모든 회의의 id를 모아 직원·어르신 각각 한 번에 읽는다
        Set<Long> staffIds = new LinkedHashSet<>();
        for (LabCaseMeeting m : rows) {
            staffIds.addAll(LabJson.readIds(m.getAttendeeIds()));
            for (StoredOpinion o : LabJson.readList(m.getOpinions(), StoredOpinion.class)) {
                staffIds.add(o.memberId());
            }
            for (StoredTarget t : LabJson.readList(m.getTargets(), StoredTarget.class)) {
                if (t.ownerMemberId() != null) {
                    staffIds.add(t.ownerMemberId());
                }
            }
        }
        Map<Long, String> staffNames = staff.namesOf(staffIds, companyId);
        Map<Long, String> elderNames = rows.isEmpty() ? Map.of() : elders.namesInCompany(companyId);
        return rows.stream().map(m -> view(m, staffNames, elderNames)).toList();
    }

    @Transactional
    public LabCaseMeetingDto.View create(LabCaseMeetingDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabCaseMeeting meeting = new LabCaseMeeting(caller.companyId());
        apply(meeting, input, caller);
        return viewOf(meetings.saveAndFlush(meeting), caller.companyId());
    }

    @Transactional
    public LabCaseMeetingDto.View update(Long id, LabCaseMeetingDto.Input input) {
        LabAccessService.Caller caller = access.requireCaller();
        LabCaseMeeting meeting = meetings.findByIdAndCompanyId(id, caller.companyId())
                .orElseThrow(LabCaseMeetingService::notFound);
        apply(meeting, input, caller);
        return viewOf(meetings.saveAndFlush(meeting), caller.companyId());
    }

    @Transactional
    public void delete(Long id) {
        Long companyId = access.requireCompanyId();
        meetings.delete(meetings.findByIdAndCompanyId(id, companyId).orElseThrow(LabCaseMeetingService::notFound));
    }

    private void apply(LabCaseMeeting meeting, LabCaseMeetingDto.Input input, LabAccessService.Caller caller) {
        Long companyId = caller.companyId();
        if (input == null) {
            throw LabValidation.bad("사례관리 회의 내용을 보내 주세요");
        }
        LocalDate heldOn = LabValidation.pastOrToday(input.heldOn(), "회의일");
        List<Long> attendeeIds = LabValidation.distinctIds(input.attendeeIds(), 1, MAX_ATTENDEES, "참석자");
        List<StoredOpinion> opinions = opinions(input.opinions(), attendeeIds);
        List<StoredTarget> targets = targets(input.targets(), heldOn);
        String minutes = LabValidation.text(input.minutes(), MINUTES, "회의록");

        // 입력 모양이 맞은 뒤에 기관 소속을 확인한다 — 하나라도 다른 기관이면 통째로 404
        staff.requireAllInCompany(attendeeIds, companyId);
        Set<Long> owners = new LinkedHashSet<>();
        for (StoredTarget t : targets) {
            if (t.ownerMemberId() != null) {
                owners.add(t.ownerMemberId());
            }
        }
        staff.requireAllInCompany(owners, companyId);
        elders.requireAllInCompany(targets.stream().map(StoredTarget::elderlyId).toList(), companyId);

        meeting.fill(heldOn, LabJson.write(attendeeIds), LabJson.write(opinions), LabJson.write(targets), minutes,
                LabValidation.writerName(caller), Boolean.TRUE.equals(input.aiAssisted()));
    }

    /** 의견 — 참석자의 것만, 참석자마다 하나. 비어 있는 의견은 '의견 없음'(null)으로 기록한다 */
    private static List<StoredOpinion> opinions(List<LabCaseMeetingDto.OpinionInput> input, List<Long> attendeeIds) {
        if (input == null) {
            return List.of();
        }
        Set<Long> attendees = new HashSet<>(attendeeIds);
        Set<Long> seen = new HashSet<>();
        List<StoredOpinion> result = new ArrayList<>();
        for (LabCaseMeetingDto.OpinionInput o : input) {
            if (o == null || o.memberId() == null) {
                throw LabValidation.bad("의견을 낸 참석자를 선택해 주세요");
            }
            if (!attendees.contains(o.memberId())) {
                throw LabValidation.bad("의견은 참석자만 낼 수 있습니다. 참석자에 없는 사람의 의견이 들어 있습니다");
            }
            if (!seen.add(o.memberId())) {
                throw LabValidation.bad("참석자 한 명의 의견은 하나만 쓸 수 있습니다");
            }
            result.add(new StoredOpinion(o.memberId(), LabValidation.text(o.opinion(), OPINION, "의견")));
        }
        return result;
    }

    private static List<StoredTarget> targets(List<LabCaseMeetingDto.TargetInput> input, LocalDate heldOn) {
        if (input == null || input.isEmpty()) {
            throw LabValidation.bad("사례관리 대상 어르신을 1명 이상 입력해 주세요");
        }
        if (input.size() > MAX_TARGETS) {
            throw LabValidation.bad("사례관리 대상은 한 회의에 최대 " + MAX_TARGETS + "명까지 입력할 수 있습니다");
        }
        Set<Long> seen = new HashSet<>();
        List<StoredTarget> result = new ArrayList<>();
        for (LabCaseMeetingDto.TargetInput t : input) {
            if (t == null || t.elderlyId() == null) {
                throw LabValidation.bad("사례관리 대상 어르신을 선택해 주세요");
            }
            if (!seen.add(t.elderlyId())) {
                throw LabValidation.bad("같은 어르신이 대상에 두 번 들어 있습니다");
            }
            result.add(target(t, heldOn));
        }
        return result;
    }

    private static StoredTarget target(LabCaseMeetingDto.TargetInput t, LocalDate heldOn) {
        String reason = LabValidation.requiredText(t.reason(), REASON, "선정 사유");
        String plan = LabValidation.requiredText(t.plan(), PLAN, "사례관리 계획");

        LocalDate reflectedOn = LabValidation.pastOrTodayOrNull(t.reflectedOn(), "반영일");
        if (reflectedOn != null && reflectedOn.isBefore(heldOn)) {
            throw LabValidation.bad("반영일은 회의일보다 앞설 수 없습니다");
        }
        String method = t.reflectMethod() == null || t.reflectMethod().isBlank() ? null
                : LabValidation.oneOf(t.reflectMethod(), REFLECT_METHODS, "반영 방식");
        if (reflectedOn != null && method == null) {
            throw LabValidation.bad("반영일이 있으면 반영 방식(돌봄계획·돌봄기록·자원연계·기관 내부)을 선택해 주세요");
        }
        String reflectNote = LabValidation.text(t.reflectNote(), REFLECT_NOTE, "반영 내용");

        LocalDate evaluatedOn = LabValidation.pastOrTodayOrNull(t.evaluatedOn(), "평가일");
        String evaluator = LabValidation.text(t.evaluatorName(), EVALUATOR, "평가자");
        String nextAction = LabValidation.text(t.nextAction(), NEXT_ACTION, "추가 조치");
        if (evaluatedOn != null) {
            if (reflectedOn == null) {
                throw LabValidation.bad("반영하기 전에는 평가할 수 없습니다. 반영일을 먼저 입력해 주세요");
            }
            if (evaluatedOn.isBefore(reflectedOn)) {
                throw LabValidation.bad("평가일은 반영일보다 앞설 수 없습니다");
            }
            if (evaluator == null) {
                throw LabValidation.bad("평가했으면 평가자를 입력해 주세요");
            }
            if (t.resolved() == null) {
                throw LabValidation.bad("평가했으면 해결 여부를 선택해 주세요");
            }
            if (!t.resolved() && nextAction == null) {
                throw LabValidation.bad("해결되지 않았으면 추가 조치를 입력해 주세요");
            }
        }
        return new StoredTarget(t.elderlyId(), reason, plan, t.ownerMemberId(),
                reflectedOn == null ? null : reflectedOn.toString(), method, reflectNote,
                evaluatedOn == null ? null : evaluatedOn.toString(), evaluator, t.resolved(), nextAction);
    }

    private LabCaseMeetingDto.View viewOf(LabCaseMeeting meeting, Long companyId) {
        Set<Long> staffIds = new LinkedHashSet<>(LabJson.readIds(meeting.getAttendeeIds()));
        for (StoredTarget t : LabJson.readList(meeting.getTargets(), StoredTarget.class)) {
            if (t.ownerMemberId() != null) {
                staffIds.add(t.ownerMemberId());
            }
        }
        return view(meeting, staff.namesOf(staffIds, companyId), elders.namesInCompany(companyId));
    }

    private static LabCaseMeetingDto.View view(LabCaseMeeting m, Map<Long, String> staffNames,
                                               Map<Long, String> elderNames) {
        List<LabCaseMeetingDto.Attendee> attendees = LabJson.readIds(m.getAttendeeIds()).stream()
                .map(id -> new LabCaseMeetingDto.Attendee(id, staffNames.getOrDefault(id, ""))).toList();
        List<LabCaseMeetingDto.Opinion> opinions = LabJson.readList(m.getOpinions(), StoredOpinion.class).stream()
                .map(o -> new LabCaseMeetingDto.Opinion(o.memberId(), staffNames.getOrDefault(o.memberId(), ""),
                        o.opinion())).toList();
        List<LabCaseMeetingDto.Target> targets = LabJson.readList(m.getTargets(), StoredTarget.class).stream()
                .map(t -> new LabCaseMeetingDto.Target(t.elderlyId(), elderNames.getOrDefault(t.elderlyId(), ""),
                        t.reason(), t.plan(), t.ownerMemberId(),
                        t.ownerMemberId() == null ? null : staffNames.getOrDefault(t.ownerMemberId(), ""),
                        date(t.reflectedOn()), t.reflectMethod(), t.reflectNote(), date(t.evaluatedOn()),
                        t.evaluatorName(), t.resolved(), t.nextAction())).toList();
        return new LabCaseMeetingDto.View(m.getId(), m.getHeldOn(), attendees, opinions, targets, m.getMinutes(),
                m.getWriterName(), m.isAiAssisted(), m.getUpdatedAt());
    }

    private static LocalDate date(String iso) {
        return iso == null ? null : LocalDate.parse(iso);
    }

    private static CustomException notFound() {
        return new CustomException("사례관리 회의 기록을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
