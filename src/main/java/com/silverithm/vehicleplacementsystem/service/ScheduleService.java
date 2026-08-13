package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.silverithm.vehicleplacementsystem.util.PrivacyMask;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleLabelRepository scheduleLabelRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final FCMService fcmService;
    private final ResourceScopeGuard resourceScopeGuard;

    // ==================== Schedule CRUD ====================

    /**
     * 일정 생성
     */
    @Transactional
    public ScheduleDTO createSchedule(Long companyId, String authorId, String authorName,
                                       ScheduleRequestDTO request) {
        log.info("[Schedule Service] 일정 생성: companyId={}, author={}", companyId, authorName);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        ScheduleLabel label = null;
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
        }

        Schedule schedule = Schedule.builder()
                .company(company)
                .title(request.getTitle())
                .content(request.getContent())
                .category(parseCategory(request.getCategory()))
                .label(label)
                .location(request.getLocation())
                .startDate(request.getStartDate())
                .startTime(request.getStartTime())
                .endDate(request.getEndDate())
                .endTime(request.getEndTime())
                .isAllDay(request.getIsAllDay())
                .sendNotification(request.getSendNotification() != null ? request.getSendNotification() : false)
                .authorId(authorId)
                .authorName(authorName)
                .build();

        applyManager(schedule, request.getManagerId());

        Schedule saved = scheduleRepository.save(schedule);
        log.info("[Schedule Service] 일정 저장 완료: id={}", saved.getId());

        // 참석자 추가 — 한 번에 조회한다. 한 명씩 findById로 돌면 참석자 수만큼 쿼리가 나간다.
        if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            List<ScheduleParticipant> participants = buildParticipants(saved, request.getParticipantIds());
            if (!participants.isEmpty()) {
                scheduleParticipantRepository.saveAll(participants);
                log.info("[Schedule Service] 참석자 {} 명 추가 완료", participants.size());

                // 알림 전송
                if (Boolean.TRUE.equals(request.getSendNotification())) {
                    sendNotificationsToParticipants(participants, saved);
                }
            }
        }

        return ScheduleDTO.fromEntity(scheduleRepository.findById(saved.getId()).orElse(saved));
    }

    /** 담당자 지정/해제 — memberId가 유효하면 이름을 조회해 함께 저장, null이면 해제 */
    private void applyManager(Schedule schedule, Long managerId) {
        if (managerId == null) {
            schedule.setManagerMemberId(null);
            schedule.setManagerName(null);
            return;
        }
        Member manager = memberRepository.findById(managerId).orElse(null);
        if (manager != null) {
            schedule.setManagerMemberId(manager.getId());
            schedule.setManagerName(manager.getName());
        }
    }

    /**
     * 일정 수정
     */
    @Transactional
    public ScheduleDTO updateSchedule(Long scheduleId, ScheduleRequestDTO request) {
        log.info("[Schedule Service] 일정 수정: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));
        resourceScopeGuard.requireSameCompany(schedule.getCompany());

        ScheduleLabel label = null;
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
        }

        schedule.update(
                request.getTitle(),
                request.getContent(),
                request.getCategory() != null ? parseCategory(request.getCategory()) : null,
                label,
                request.getLocation(),
                request.getStartDate(),
                request.getStartTime(),
                request.getEndDate(),
                request.getEndTime(),
                request.getIsAllDay(),
                request.getSendNotification()
        );

        applyManager(schedule, request.getManagerId());

        Schedule saved = scheduleRepository.save(schedule);
        log.info("[Schedule Service] 일정 수정 완료: id={}", saved.getId());

        // 기존 참석자 삭제
        scheduleParticipantRepository.deleteByScheduleId(scheduleId);

        // 새로운 참석자 추가
        if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            List<ScheduleParticipant> participants = buildParticipants(saved, request.getParticipantIds());
            if (!participants.isEmpty()) {
                scheduleParticipantRepository.saveAll(participants);
                log.info("[Schedule Service] 참석자 {} 명 업데이트 완료", participants.size());

                // 알림 전송
                if (Boolean.TRUE.equals(request.getSendNotification())) {
                    sendNotificationsToParticipants(participants, saved);
                }
            }
        }

        return ScheduleDTO.fromEntity(scheduleRepository.findById(saved.getId()).orElse(saved));
    }

    /**
     * 일정 수행완료 상태 변경 (진행도 체크)
     * 담당자가 지정된 일정은 담당자 본인 또는 관리자(대행), 미지정 일정은 작성자 본인 또는 관리자만 변경 가능하다.
     */
    @Transactional
    public ScheduleDTO updateCompletion(Long scheduleId, boolean completed, String userId,
                                        String userName, Long memberId, boolean isAdmin) {
        log.info("[Schedule Service] 일정 수행완료 변경: id={}, completed={}, user={}", scheduleId, completed, userId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));
        resourceScopeGuard.requireSameCompany(schedule.getCompany());

        Long managerMemberId = schedule.getManagerMemberId();
        if (managerMemberId != null) {
            if (!managerMemberId.equals(memberId) && !isAdmin) {
                throw new IllegalStateException("담당자 또는 관리자만 수행완료 처리할 수 있습니다.");
            }
        } else {
            boolean isAuthor = userId != null && userId.equals(schedule.getAuthorId());
            if (!isAdmin && !isAuthor) {
                throw new IllegalStateException("본인이 등록한 일정만 수행완료 처리할 수 있습니다.");
            }
        }

        schedule.updateCompletion(completed, userId, userName);
        Schedule saved = scheduleRepository.save(schedule);

        return ScheduleDTO.fromEntity(saved);
    }

    // ==================== 할 일(ScheduleTask) ====================

    /**
     * 할 일 추가. 기관 구성원 누구나 추가할 수 있다.
     */
    @Transactional
    public ScheduleTaskDTO createTask(Long scheduleId, ScheduleTaskRequestDTO request,
                                      String userId, String userName) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("할 일 내용을 입력해주세요.");
        }

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));
        resourceScopeGuard.requireSameCompany(schedule.getCompany());

        String assigneeName = resolveMemberName(request.getAssigneeMemberId());
        int nextOrder = (int) scheduleTaskRepository.countByScheduleId(scheduleId);

        ScheduleTask task = ScheduleTask.builder()
                .schedule(schedule)
                .content(request.getContent().trim())
                .assigneeMemberId(request.getAssigneeMemberId())
                .assigneeName(assigneeName)
                .isCompleted(false)
                .createdById(userId)
                .createdByName(userName)
                .sortOrder(nextOrder)
                .build();

        ScheduleTask saved = scheduleTaskRepository.save(task);
        scheduleTaskRepository.flush();
        log.info("[Schedule Service] 할 일 추가: scheduleId={}, taskId={}", scheduleId, saved.getId());

        // 방금 추가한 항목까지 반영해 일정 완료 상태를 다시 계산한다
        syncScheduleCompletion(schedule);
        notifyAssignee(saved, "새로운 업무가 배정되었습니다");

        return ScheduleTaskDTO.fromEntity(saved);
    }

    /**
     * 할 일 내용·담당자 수정. 작성자·관리자·담당자 본인이 수정할 수 있다.
     */
    @Transactional
    public ScheduleTaskDTO updateTask(Long taskId, ScheduleTaskRequestDTO request,
                                      String userId, Long memberId, boolean isAdmin) {
        ScheduleTask task = scheduleTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("할 일을 찾을 수 없습니다: " + taskId));
        resourceScopeGuard.requireSameCompany(
                task.getSchedule() != null ? task.getSchedule().getCompany() : null);

        if (!canEditTask(task, userId, memberId, isAdmin)) {
            throw new IllegalStateException("이 할 일을 수정할 권한이 없습니다.");
        }

        Long previousAssignee = task.getAssigneeMemberId();
        task.updateContent(
                request.getContent(),
                request.getAssigneeMemberId(),
                resolveMemberName(request.getAssigneeMemberId())
        );

        ScheduleTask saved = scheduleTaskRepository.save(task);

        boolean assigneeChanged = request.getAssigneeMemberId() != null
                && !request.getAssigneeMemberId().equals(previousAssignee);
        if (assigneeChanged) {
            notifyAssignee(saved, "새로운 업무가 배정되었습니다");
        }

        return ScheduleTaskDTO.fromEntity(saved);
    }

    /**
     * 할 일 삭제. 작성자·관리자·담당자 본인이 삭제할 수 있다.
     */
    @Transactional
    public void deleteTask(Long taskId, String userId, Long memberId, boolean isAdmin) {
        ScheduleTask task = scheduleTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("할 일을 찾을 수 없습니다: " + taskId));
        resourceScopeGuard.requireSameCompany(
                task.getSchedule() != null ? task.getSchedule().getCompany() : null);

        if (!canEditTask(task, userId, memberId, isAdmin)) {
            throw new IllegalStateException("이 할 일을 삭제할 권한이 없습니다.");
        }

        Schedule schedule = task.getSchedule();
        scheduleTaskRepository.delete(task);
        scheduleTaskRepository.flush();

        syncScheduleCompletion(schedule);
        log.info("[Schedule Service] 할 일 삭제: taskId={}", taskId);
    }

    /**
     * 할 일 수행완료 토글.
     * 담당자 본인이 체크하는 것이 원칙이고, 관리자는 대신 처리할 수 있다.
     * 담당자가 지정되지 않은 할 일은 기관 구성원 누구나 처리할 수 있다.
     */
    @Transactional
    public ScheduleTaskDTO updateTaskCompletion(Long taskId, boolean completed,
                                                String userId, String userName,
                                                Long memberId, boolean isAdmin) {
        ScheduleTask task = scheduleTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("할 일을 찾을 수 없습니다: " + taskId));
        resourceScopeGuard.requireSameCompany(
                task.getSchedule() != null ? task.getSchedule().getCompany() : null);

        boolean isAssignee = task.getAssigneeMemberId() != null
                && task.getAssigneeMemberId().equals(memberId);
        boolean unassigned = task.getAssigneeMemberId() == null;

        if (!unassigned && !isAssignee && !isAdmin) {
            throw new IllegalStateException("담당자 본인 또는 관리자만 수행완료 처리할 수 있습니다.");
        }

        task.updateCompletion(completed, userId, userName);
        ScheduleTask saved = scheduleTaskRepository.save(task);
        scheduleTaskRepository.flush();

        // 할 일이 모두 끝나면 일정도 완료로 넘어간다
        syncScheduleCompletion(task.getSchedule());

        log.info("[Schedule Service] 할 일 완료 변경: taskId={}, completed={}, user={}", taskId, completed, userId);
        return ScheduleTaskDTO.fromEntity(saved);
    }

    /**
     * 일정의 할 일 목록
     */
    @Transactional(readOnly = true)
    public List<ScheduleTaskDTO> getTasks(Long scheduleId) {
        return scheduleTaskRepository.findByScheduleIdOrderBySortOrderAscIdAsc(scheduleId).stream()
                .map(ScheduleTaskDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 내 할 일 목록 (대시보드 위젯 / 내 업무 필터용)
     */
    @Transactional(readOnly = true)
    public List<ScheduleTaskDTO> getMyTasks(Long companyId, Long memberId,
                                            LocalDate startDate, LocalDate endDate) {
        if (memberId == null) {
            return List.of();
        }
        return scheduleTaskRepository.findByAssignee(companyId, memberId, startDate, endDate).stream()
                .map(ScheduleTaskDTO::fromEntityWithSchedule)
                .collect(Collectors.toList());
    }

    /**
     * 할 일 진행 상황에 따라 일정의 완료 플래그를 맞춘다.
     * 할 일이 하나도 없으면 일정 완료는 수동 처리 영역이므로 건드리지 않는다.
     */
    private void syncScheduleCompletion(Schedule schedule) {
        long total = scheduleTaskRepository.countByScheduleId(schedule.getId());
        if (total == 0) {
            return;
        }

        long done = scheduleTaskRepository.countByScheduleIdAndIsCompletedTrue(schedule.getId());
        boolean allDone = done == total;

        if (Boolean.TRUE.equals(schedule.getIsCompleted()) == allDone) {
            return;
        }

        if (allDone) {
            schedule.updateCompletion(true, null, "할 일 전체 완료");
        } else {
            schedule.updateCompletion(false, null, null);
        }
        scheduleRepository.save(schedule);
        log.info("[Schedule Service] 할 일 진행에 따라 일정 완료 상태 변경: scheduleId={}, completed={}",
                schedule.getId(), allDone);
    }

    private boolean canEditTask(ScheduleTask task, String userId, Long memberId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (userId != null && userId.equals(task.getCreatedById())) {
            return true;
        }
        if (userId != null && userId.equals(task.getSchedule().getAuthorId())) {
            return true;
        }
        return task.getAssigneeMemberId() != null && task.getAssigneeMemberId().equals(memberId);
    }

    private String resolveMemberName(Long memberId) {
        if (memberId == null) {
            return null;
        }
        return memberRepository.findById(memberId).map(Member::getName).orElse(null);
    }

    private void notifyAssignee(ScheduleTask task, String title) {
        if (task.getAssigneeMemberId() == null) {
            return;
        }
        try {
            Member member = memberRepository.findById(task.getAssigneeMemberId()).orElse(null);
            if (member == null || member.getFcmToken() == null || member.getFcmToken().isEmpty()) {
                return;
            }
            String body = String.format("%s - %s", task.getSchedule().getTitle(), task.getContent());
            fcmService.sendNotification(member.getFcmToken(), title, body);
        } catch (Exception e) {
            log.error("[Schedule Service] 할 일 알림 전송 실패: taskId={}", task.getId(), e);
        }
    }

    /**
     * 일정 삭제
     */
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        log.info("[Schedule Service] 일정 삭제: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));
        resourceScopeGuard.requireSameCompany(schedule.getCompany());

        scheduleRepository.delete(schedule);
        log.info("[Schedule Service] 일정 삭제 완료: id={}", scheduleId);
    }

    /**
     * 일정 상세 조회
     */
    @Transactional(readOnly = true)
    public ScheduleDTO getSchedule(Long scheduleId) {
        log.info("[Schedule Service] 일정 조회: id={}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("일정을 찾을 수 없습니다: " + scheduleId));
        resourceScopeGuard.requireSameCompany(schedule.getCompany());

        return ScheduleDTO.fromEntity(schedule);
    }

    /**
     * 일정 목록 조회 (기간별)
     */
    @Transactional(readOnly = true)
    public List<ScheduleDTO> getSchedules(Long companyId, LocalDate startDate, LocalDate endDate,
                                           String category, Long labelId, String searchQuery) {
        log.info("[Schedule Service] 일정 목록 조회: companyId={}, {} ~ {}", companyId, startDate, endDate);

        ScheduleCategory scheduleCategory = null;
        if (category != null && !category.equals("ALL") && !category.isEmpty()) {
            scheduleCategory = parseCategory(category);
        }

        List<Schedule> schedules;

        if (startDate != null && endDate != null) {
            if (scheduleCategory != null || labelId != null || (searchQuery != null && !searchQuery.isEmpty())) {
                schedules = scheduleRepository.findByFilters(
                        companyId, scheduleCategory, labelId, searchQuery, startDate, endDate);
            } else {
                schedules = scheduleRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate);
            }
        } else {
            schedules = scheduleRepository.findByCompanyIdOrderByStartDateAscStartTimeAsc(companyId);
        }

        return schedules.stream()
                .map(ScheduleDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getStats(Long companyId) {
        long total = scheduleRepository.countByCompanyId(companyId);
        long meeting = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.MEETING);
        long event = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.EVENT);
        long training = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.TRAINING);
        long other = scheduleRepository.countByCompanyIdAndCategory(companyId, ScheduleCategory.OTHER);

        return Map.of(
                "total", total,
                "meeting", meeting,
                "event", event,
                "training", training,
                "other", other
        );
    }

    // ==================== ScheduleLabel CRUD ====================

    /**
     * 라벨 생성
     */
    @Transactional
    public ScheduleLabelDTO createLabel(Long companyId, ScheduleLabelRequestDTO request) {
        log.info("[Schedule Service] 라벨 생성: companyId={}, name={}", companyId, request.getName());

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        // 중복 이름 체크
        if (scheduleLabelRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new RuntimeException("이미 존재하는 라벨 이름입니다: " + request.getName());
        }

        ScheduleLabel label = ScheduleLabel.builder()
                .company(company)
                .name(request.getName())
                .color(request.getColor())
                .build();

        ScheduleLabel saved = scheduleLabelRepository.save(label);
        log.info("[Schedule Service] 라벨 저장 완료: id={}", saved.getId());

        return ScheduleLabelDTO.fromEntity(saved);
    }

    /**
     * 라벨 수정
     */
    @Transactional
    public ScheduleLabelDTO updateLabel(Long labelId, ScheduleLabelRequestDTO request) {
        log.info("[Schedule Service] 라벨 수정: id={}", labelId);

        ScheduleLabel label = scheduleLabelRepository.findById(labelId)
                .orElseThrow(() -> new RuntimeException("라벨을 찾을 수 없습니다: " + labelId));
        resourceScopeGuard.requireSameCompany(label.getCompany());

        // 중복 이름 체크 (자기 자신 제외)
        if (request.getName() != null &&
            scheduleLabelRepository.existsByCompanyIdAndNameAndIdNot(
                    label.getCompany().getId(), request.getName(), labelId)) {
            throw new RuntimeException("이미 존재하는 라벨 이름입니다: " + request.getName());
        }

        label.update(request.getName(), request.getColor());

        ScheduleLabel saved = scheduleLabelRepository.save(label);
        log.info("[Schedule Service] 라벨 수정 완료: id={}", saved.getId());

        return ScheduleLabelDTO.fromEntity(saved);
    }

    /**
     * 라벨 삭제.
     *
     * 예전에는 그 라벨을 쓰는 일정이 하나라도 있으면 삭제를 거부했다. 그러면 라벨을 정리하려고
     * 지난 일정을 하나씩 열어 라벨을 바꿔야 해서 사실상 지울 수 없었다.
     * 이제는 참조를 떼고(일정은 "라벨 없음"으로 남는다) 라벨만 지운다. 일정 자체는 사라지지 않는다.
     *
     * @return 라벨이 떨어진 일정 수 (화면 안내용)
     */
    @Transactional
    public long deleteLabel(Long labelId) {
        log.info("[Schedule Service] 라벨 삭제: id={}", labelId);

        ScheduleLabel label = scheduleLabelRepository.findById(labelId)
                .orElseThrow(() -> new RuntimeException("라벨을 찾을 수 없습니다: " + labelId));
        resourceScopeGuard.requireSameCompany(label.getCompany());

        List<Schedule> using = scheduleRepository.findByLabelId(labelId);
        for (Schedule schedule : using) {
            schedule.setLabel(null);
        }
        if (!using.isEmpty()) {
            scheduleRepository.saveAll(using);
        }

        scheduleLabelRepository.delete(label);
        log.info("[Schedule Service] 라벨 삭제 완료: id={}, 참조 해제 {}건", labelId, using.size());
        return using.size();
    }

    /**
     * 라벨 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ScheduleLabelDTO> getLabels(Long companyId) {
        log.info("[Schedule Service] 라벨 목록 조회: companyId={}", companyId);

        List<ScheduleLabel> labels = scheduleLabelRepository.findByCompanyIdOrderByNameAsc(companyId);

        return labels.stream()
                .map(ScheduleLabelDTO::fromEntity)
                .collect(Collectors.toList());
    }

    // ==================== Helper Methods ====================

    private ScheduleCategory parseCategory(String category) {
        if (category == null) {
            return ScheduleCategory.OTHER;
        }
        try {
            return ScheduleCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Schedule Service] 알 수 없는 카테고리: {}", category);
            return ScheduleCategory.OTHER;
        }
    }

    /**
     * 요청받은 회원 id로 참석자 목록을 만든다. 없는 id는 건너뛴다.
     *
     * 한 번에 조회한 뒤 요청받은 순서대로 다시 세운다 — findAllById는 순서를 보장하지 않는데,
     * 참석자는 화면에서 고른 순서대로 보이는 편이 자연스럽다.
     */
    private List<ScheduleParticipant> buildParticipants(Schedule schedule, List<Long> memberIds) {
        Map<Long, Member> membersById = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        List<ScheduleParticipant> participants = new ArrayList<>();
        for (Long memberId : memberIds) {
            Member member = membersById.get(memberId);
            if (member == null) {
                continue;
            }
            participants.add(ScheduleParticipant.builder()
                    .schedule(schedule)
                    .memberId(member.getId())
                    .memberName(member.getName())
                    .build());
        }
        return participants;
    }

    /**
     * 일정 알림에 실어 보낼 데이터.
     *
     * 이게 없으면 알림은 뜨지만 눌러도 앱이 어디로 갈지 알 수 없어 아무 일도 일어나지 않는다.
     * 앱은 별도 일정 상세 화면 없이 날짜를 골라 펼치는 구조라 시작일을 함께 보낸다.
     */
    static Map<String, String> scheduleNotificationData(Schedule schedule) {
        Map<String, String> data = new HashMap<>();
        data.put("type", "schedule");
        data.put("scheduleId", String.valueOf(schedule.getId()));
        if (schedule.getStartDate() != null) {
            data.put("scheduleDate", schedule.getStartDate().toString());
        }
        return data;
    }

    /**
     * 참석자들에게 FCM 알림 전송
     */
    private void sendNotificationsToParticipants(List<ScheduleParticipant> participants, Schedule schedule) {
        log.info("[Schedule Service] 참석자 {}명에게 알림 전송 시작", participants.size());

        // 참석자를 한 번에 조회해둔다 — 한 명씩 찾으면 인원수만큼 쿼리가 나간다.
        Map<Long, Member> membersById = memberRepository.findAllById(
                        participants.stream().map(ScheduleParticipant::getMemberId).toList()).stream()
                .collect(Collectors.toMap(Member::getId, member -> member));

        for (ScheduleParticipant participant : participants) {
            try {
                Member member = membersById.get(participant.getMemberId());
                if (member != null && member.getFcmToken() != null && !member.getFcmToken().isEmpty()) {
                    String title = "새로운 일정 알림";
                    String body = String.format("%s - %s", schedule.getTitle(),
                            schedule.getStartDate().toString());

                    fcmService.sendNotification(member.getFcmToken(), title, body,
                            scheduleNotificationData(schedule));
                    log.info("[Schedule Service] 알림 전송 완료: member={}", PrivacyMask.name(member.getName()));
                } else {
                    log.debug("[Schedule Service] FCM 토큰 없음: memberId={}", participant.getMemberId());
                }
            } catch (Exception e) {
                log.error("[Schedule Service] 알림 전송 실패: memberId={}",
                        participant.getMemberId(), e);
            }
        }
    }
}
