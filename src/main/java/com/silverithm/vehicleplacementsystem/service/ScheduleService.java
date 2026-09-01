package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ScheduleCategorySettingDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleCategorySettingRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleLabelRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskDTO;
import com.silverithm.vehicleplacementsystem.dto.ScheduleTaskRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Schedule;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategorySetting;
import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleCategorySettingRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleLabelRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleParticipantRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleRepository;
import com.silverithm.vehicleplacementsystem.repository.ScheduleTaskRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ScheduleCategorySettingRepository scheduleCategorySettingRepository;
    private final ScheduleLabelRepository scheduleLabelRepository;
    private final ScheduleParticipantRepository scheduleParticipantRepository;
    private final ScheduleTaskRepository scheduleTaskRepository;
    private final CompanyRepository companyRepository;
    private final PlatformTransactionManager transactionManager;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
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
        String color = request.getColor();
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
            // 구버전 웹 호환: labelId만 보내고 color 필드가 아예 없는(=null) 요청에 한해
            // schedule.color에 라벨 색을 복사해 둔다. color가 명시적으로 왔으면(빈 문자열 포함)
            // 그 값이 우선이다 — labelId와 color를 함께 보내는 클라이언트가 고른 색을 라벨 색으로
            // 조용히 덮어쓰면 안 된다.
            if (label != null && request.getColor() == null) {
                color = label.getColor();
            }
        }

        Schedule schedule = Schedule.builder()
                .company(company)
                .title(request.getTitle())
                .content(request.getContent())
                .category(parseCategory(request.getCategory()))
                .label(label)
                .color(normalizeColor(color))
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

        applyManager(schedule, request.getManagerId(), request.getManagerType());

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

        return ScheduleDTO.fromEntity(scheduleRepository.findById(saved.getId()).orElse(saved),
                categorySettingsFor(saved.getCompany().getId()));
    }

    /**
     * 담당자 지정/해제.
     * managerId가 유효하면 종류(MEMBER/members | ADMIN/app_user)에 맞는 테이블에서 조회해 이름과 함께 저장하고,
     * null이면 해제한다. managerType이 비어 있으면(구버전 클라이언트) MEMBER로 본다.
     * 조회된 담당자가 이 일정의 회사 소속이 아니면 저장하지 않는다 — 다른 회사 id를 넣는 시도를 막기 위함(IDOR 방지).
     */
    private void applyManager(Schedule schedule, Long managerId, String managerTypeRaw) {
        if (managerId == null) {
            schedule.setManagerMemberId(null);
            schedule.setManagerName(null);
            schedule.setManagerType(Schedule.ManagerType.MEMBER);
            return;
        }

        Schedule.ManagerType managerType;
        try {
            managerType = (managerTypeRaw == null || managerTypeRaw.isBlank())
                    ? Schedule.ManagerType.MEMBER
                    : Schedule.ManagerType.valueOf(managerTypeRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[Schedule Service] 알 수 없는 담당자 종류: {} - MEMBER로 처리", managerTypeRaw);
            managerType = Schedule.ManagerType.MEMBER;
        }

        Long scheduleCompanyId = schedule.getCompany() != null ? schedule.getCompany().getId() : null;

        if (managerType == Schedule.ManagerType.ADMIN) {
            AppUser manager = userRepository.findById(managerId).orElse(null);
            if (manager == null) {
                log.warn("[Schedule Service] 존재하지 않는 관리자 담당자 id={} - 담당자 지정 무시", managerId);
                return;
            }
            Long managerCompanyId = manager.getCompany() != null ? manager.getCompany().getId() : null;
            if (scheduleCompanyId == null || !scheduleCompanyId.equals(managerCompanyId)) {
                log.warn("[Schedule Service] 담당자(관리자) 회사 불일치: scheduleCompany={}, managerCompany={} - 담당자 지정 무시",
                        scheduleCompanyId, managerCompanyId);
                return;
            }
            schedule.setManagerMemberId(manager.getId());
            schedule.setManagerName(manager.getUsername());
            schedule.setManagerType(Schedule.ManagerType.ADMIN);
        } else {
            Member manager = memberRepository.findById(managerId).orElse(null);
            if (manager == null) {
                log.warn("[Schedule Service] 존재하지 않는 직원 담당자 id={} - 담당자 지정 무시", managerId);
                return;
            }
            Long managerCompanyId = manager.getCompany() != null ? manager.getCompany().getId() : null;
            if (scheduleCompanyId == null || !scheduleCompanyId.equals(managerCompanyId)) {
                log.warn("[Schedule Service] 담당자(직원) 회사 불일치: scheduleCompany={}, managerCompany={} - 담당자 지정 무시",
                        scheduleCompanyId, managerCompanyId);
                return;
            }
            schedule.setManagerMemberId(manager.getId());
            schedule.setManagerName(manager.getName());
            schedule.setManagerType(Schedule.ManagerType.MEMBER);
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
        String color = request.getColor();
        if (request.getLabelId() != null) {
            label = scheduleLabelRepository.findById(request.getLabelId())
                    .orElse(null);
            // 구버전 웹 호환: labelId만 보내고 color 필드가 아예 없는(=null) 요청에 한해
            // schedule.color에 라벨 색을 복사해 둔다. color가 명시적으로 왔으면(빈 문자열 포함)
            // 그 값이 우선이다 — labelId와 color를 함께 보내는 클라이언트가 고른 색을 라벨 색으로
            // 조용히 덮어쓰면 안 된다.
            if (label != null && request.getColor() == null) {
                color = label.getColor();
            }
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
                request.getSendNotification(),
                color
        );

        applyManager(schedule, request.getManagerId(), request.getManagerType());

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

        return ScheduleDTO.fromEntity(scheduleRepository.findById(saved.getId()).orElse(saved),
                categorySettingsFor(saved.getCompany().getId()));
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
            // managerMemberId는 managerType이 MEMBER일 때만 members.id 공간이다. ADMIN이면
            // app_user.id라 memberId(항상 members.id, 관리자 계정이면 null)와 우연히 같은 값이
            // 나올 수 있어 — 그 값만 비교하면 남의 회사도 아닌 남의 계정을 "본인"으로 오인해
            // 엉뚱한 직원이 관리자 담당 일정을 완료 처리할 수 있었다(V1.88과 같은 사고 유형).
            // ADMIN 담당자의 "본인" 여부는 여기서 판정하지 않는다 — 관리자 계정은 이미
            // isAdmin=true로 항상 대행 권한이 있어 이 필드로 개인을 식별할 필요가 없다.
            boolean isSelfManager = schedule.getManagerType() == Schedule.ManagerType.MEMBER
                    && managerMemberId.equals(memberId);
            if (!isSelfManager && !isAdmin) {
                throw new IllegalStateException("담당자 또는 관리자만 수행완료 처리할 수 있습니다.");
            }
        } else {
            boolean isAuthor = userId != null && userId.equals(schedule.getAuthorId());
            if (!isAdmin && !isAuthor) {
                throw new IllegalStateException("본인이 등록한 일정만 수행완료 처리할 수 있습니다.");
            }
        }

        // 할 일이 딸린 일정의 완료는 할 일 진행에서만 자동 결정된다(syncScheduleCompletion).
        // 웹은 이 규칙대로 버튼을 숨기지만 서버가 막지 않으면 앱 등 다른 경로로 우회된다.
        if (schedule.getTasks() != null && !schedule.getTasks().isEmpty()) {
            throw new IllegalStateException("할 일이 있는 일정은 할 일을 완료하면 자동으로 수행완료됩니다.");
        }

        schedule.updateCompletion(completed, userId, userName);
        Schedule saved = scheduleRepository.save(schedule);

        return ScheduleDTO.fromEntity(saved, categorySettingsFor(saved.getCompany().getId()));
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

        return ScheduleDTO.fromEntity(schedule, categorySettingsFor(schedule.getCompany().getId()));
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

        Map<ScheduleCategory, ScheduleCategorySetting> categorySettings = categorySettingsFor(companyId);
        return schedules.stream()
                .map(schedule -> ScheduleDTO.fromEntity(schedule, categorySettings))
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

    // ==================== 기본 구분 설정 (기관별 이름·색·숨김) ====================

    /** 기관의 기본 구분 설정을 카테고리별 맵으로. ScheduleDTO 변환 시 이름·기본색 덮어쓰기에 쓴다. */
    private Map<ScheduleCategory, ScheduleCategorySetting> categorySettingsFor(Long companyId) {
        return scheduleCategorySettingRepository.findByCompanyId(companyId).stream()
                .collect(Collectors.toMap(ScheduleCategorySetting::getCategory, s -> s, (a, b) -> a));
    }

    /**
     * 기본 구분 4종의 기관별 최종 상태(설정 머지) 목록.
     */
    @Transactional(readOnly = true)
    public List<ScheduleCategorySettingDTO> getCategorySettings(Long companyId) {
        Map<ScheduleCategory, ScheduleCategorySetting> settings = categorySettingsFor(companyId);
        List<ScheduleCategorySettingDTO> result = new ArrayList<>();
        for (ScheduleCategory category : ScheduleCategory.values()) {
            result.add(ScheduleCategorySettingDTO.of(category, settings.get(category)));
        }
        return result;
    }

    /**
     * 기본 구분의 이름·색·숨김을 기관별로 바꾼다. null 필드는 유지(부분 수정).
     * 기본값과 같은 이름·색은 "커스텀 없음"(null)으로 저장해, 나중에 enum 기본값이
     * 바뀌어도 커스텀하지 않은 기관은 자연히 따라가게 한다.
     */
    public ScheduleCategorySettingDTO upsertCategorySetting(Long companyId, String categoryName,
            ScheduleCategorySettingRequestDTO request) {
        log.info("[Schedule Service] 기본 구분 설정: companyId={}, category={}", companyId, categoryName);

        ScheduleCategory category = parseCategory(categoryName);
        try {
            return upsertCategorySettingOnce(companyId, category, request);
        } catch (DataIntegrityViolationException e) {
            // 조회와 INSERT 사이에 동시 요청이 같은 행을 먼저 만든 경우다. 조용히 삼키면
            // 이번 요청의 변경이 사라지므로 한 번 더 시도한다 — 이번엔 행이 있으니 UPDATE가 된다.
            // 반드시 새 트랜잭션이어야 한다: 실패한 트랜잭션 안에서 재조회하면 REPEATABLE READ
            // 스냅샷 때문에 방금 커밋된 행이 안 보이고, 그 트랜잭션은 이미 rollback-only다.
            log.warn("[Schedule Service] 기본 구분 설정 upsert 충돌 — 새 트랜잭션으로 재시도: companyId={}, category={}",
                    companyId, category);
            return upsertCategorySettingOnce(companyId, category, request);
        }
    }

    /** 조회→적용→저장 한 판을 독립 트랜잭션으로 돈다. 충돌 재시도가 신선한 스냅샷을 얻기 위한 분리다. */
    private ScheduleCategorySettingDTO upsertCategorySettingOnce(Long companyId, ScheduleCategory category,
            ScheduleCategorySettingRequestDTO request) {
        TransactionTemplate upsertTx = new TransactionTemplate(transactionManager);
        upsertTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return upsertTx.execute(status -> {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));
            resourceScopeGuard.requireSameCompany(company);

            ScheduleCategorySetting setting = scheduleCategorySettingRepository
                    .findByCompanyIdAndCategory(companyId, category)
                    .orElseGet(() -> ScheduleCategorySetting.builder()
                            .company(company)
                            .category(category)
                            .hidden(false)
                            .build());

            applyCategorySettingRequest(setting, category, request);

            // saveAndFlush로 즉시 INSERT해 (company_id, category) 유니크 제약 위반을
            // 이 트랜잭션 안에서 확정한다. save()만 쓰면 커밋 시점에야 터져 재시도할 수 없다.
            return ScheduleCategorySettingDTO.of(category,
                    scheduleCategorySettingRepository.saveAndFlush(setting));
        });
    }

    /** upsertCategorySetting의 요청 반영 로직. 최초 시도와 충돌 재시도가 같은 규칙을 쓰도록 분리했다. */
    private void applyCategorySettingRequest(ScheduleCategorySetting setting, ScheduleCategory category,
            ScheduleCategorySettingRequestDTO request) {
        if (request.getName() != null) {
            String trimmed = request.getName().trim();
            if (trimmed.isEmpty()) {
                throw new RuntimeException("구분 이름은 비울 수 없습니다");
            }
            setting.setDisplayName(trimmed.equals(category.getDisplayName()) ? null : trimmed);
        }
        if (request.getColor() != null) {
            setting.setColor(request.getColor().equals(category.getDefaultColor()) ? null : request.getColor());
        }
        if (request.getHidden() != null) {
            setting.setHidden(request.getHidden());
        }
    }

    /** 기본 구분 설정을 기본값으로 되돌린다(행 삭제). */
    @Transactional
    public void resetCategorySetting(Long companyId, String categoryName) {
        log.info("[Schedule Service] 기본 구분 설정 초기화: companyId={}, category={}", companyId, categoryName);
        ScheduleCategory category = parseCategory(categoryName);
        scheduleCategorySettingRepository.findByCompanyIdAndCategory(companyId, category)
                .ifPresent(setting -> {
                    resourceScopeGuard.requireSameCompany(setting.getCompany());
                    scheduleCategorySettingRepository.delete(setting);
                });
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

        String previousColor = label.getColor();
        label.update(request.getName(), request.getColor());

        ScheduleLabel saved = scheduleLabelRepository.save(label);
        log.info("[Schedule Service] 라벨 수정 완료: id={}", saved.getId());

        // 백필(V1.74) 이후로는 schedule.color가 실제 표시 색이다.
        // 라벨 색이 바뀔 때 "라벨 색을 그대로 따르던" 일정들의 color를 같이 맞추지 않으면
        // 라벨 색을 바꿔도 화면(일정 자체 색)에는 예전 색이 그대로 남는다.
        // 단, 이미 라벨 색과 다른 색으로 개별 지정된 일정(schedule.color != previousColor)은
        // 사용자가 일부러 라벨과 다르게 고른 것이므로 건드리지 않는다 — 그렇지 않으면
        // 라벨 색을 바꿀 때마다 개별 지정한 색이 조용히 사라진다.
        if (request.getColor() != null && !request.getColor().equals(previousColor)) {
            List<Schedule> using = scheduleRepository.findByLabelId(labelId);
            List<Schedule> following = using.stream()
                    .filter(schedule -> previousColor.equals(schedule.getColor()))
                    .collect(Collectors.toList());
            if (!following.isEmpty()) {
                for (Schedule schedule : following) {
                    schedule.setColor(saved.getColor());
                }
                scheduleRepository.saveAll(following);
                log.info("[Schedule Service] 라벨 색 변경에 따라 일정 {}건 색 동기화(개별 지정 {}건 제외): labelId={}",
                        following.size(), using.size() - following.size(), labelId);
            }
        }

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

    /** 빈 문자열/null을 색 없음(null)으로 통일한다. schedule.color는 "값 있음" 아니면 null만 갖는다. */
    private String normalizeColor(String color) {
        return (color == null || color.isBlank()) ? null : color;
    }

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
