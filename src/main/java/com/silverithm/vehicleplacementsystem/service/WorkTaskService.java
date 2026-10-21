package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.WorkTaskDto;
import com.silverithm.vehicleplacementsystem.entity.WorkTask;
import com.silverithm.vehicleplacementsystem.entity.WorkTaskAssignee;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.WorkTaskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업무진행도 — 대시보드 오른쪽 패널의 업무 목록(2026-10-22 원장 요청 "이름은 업무진행도").
 *
 * <ul>
 *   <li>기관 전체가 같은 목록을 본다(관리자·종사자 모두 동일).</li>
 *   <li>등록할 때 담당자·기한·업무 내용·중요도·수행도를 적는다. 담당자를 비우면 등록한 사람 자신.</li>
 *   <li>고칠 수 있는 사람: 관리자, 등록한 사람, 담당자. 지울 수 있는 사람: 관리자, 등록한 사람.</li>
 *   <li>새로 배정된 담당자에게 알리고, 다 끝나면 등록한 사람에게 알린다. 매일 알림은
 *       {@link WorkTaskReminderScheduler}가 보낸다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class WorkTaskService {

    static final Set<String> IMPORTANCE = Set.of("HIGH", "NORMAL", "LOW");
    static final int CONTENT_MAX = 300;
    static final int MAX_ASSIGNEES = 20;
    /** 끝낸 업무도 이 기간 동안은 목록 아래에 남긴다 — 끝냈다는 것도 함께 보여야 한다 */
    static final int COMPLETED_VISIBLE_DAYS = 7;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WorkTaskAccess access;
    private final WorkTaskPeople people;
    private final WorkTaskRepository tasks;
    private final WorkTaskNotifier notifier;

    /** 진행 중(중요도 → 기한 → 등록순), 그 아래 최근 끝낸 것(늦게 끝낸 순) */
    @Transactional(readOnly = true)
    public List<WorkTaskDto.View> list() {
        WorkTaskAccess.Caller caller = access.requireCaller();
        LocalDateTime since = LocalDateTime.now(SEOUL).minusDays(COMPLETED_VISIBLE_DAYS);
        return tasks.findVisible(caller.companyId(), since).stream()
                .sorted(DISPLAY_ORDER)
                .map(t -> toView(t, caller))
                .toList();
    }

    @Transactional
    public WorkTaskDto.View create(WorkTaskDto.Input input) {
        WorkTaskAccess.Caller caller = access.requireCaller();
        Valid v = validate(input);
        WorkTask task = new WorkTask(caller.company(), caller.personId(), clipName(caller.name()));
        task.fill(v.content(), v.importance(), v.progress(), v.dueDate());
        List<WorkTaskAssignee> added = task.reconcileAssignees(assigneesOf(input, caller));
        WorkTask saved = tasks.saveAndFlush(task);
        notifier.sendAfterCommit(assignedMessages(saved, added, caller));
        return toView(saved, caller);
    }

    @Transactional
    public WorkTaskDto.View update(Long id, WorkTaskDto.Input input) {
        WorkTaskAccess.Caller caller = access.requireCaller();
        WorkTask task = findInCompany(id, caller);
        requireCanEdit(task, caller);
        Valid v = validate(input);
        boolean wasDone = task.isDone();
        task.fill(v.content(), v.importance(), v.progress(), v.dueDate());
        List<WorkTaskAssignee> added = task.reconcileAssignees(assigneesOf(input, caller));
        WorkTask saved = tasks.saveAndFlush(task);

        List<WorkTaskNotifier.Message> messages = new ArrayList<>(assignedMessages(saved, added, caller));
        messages.addAll(finishedMessage(saved, wasDone, caller));
        notifier.sendAfterCommit(messages);
        return toView(saved, caller);
    }

    /** 목록에서 수행도만 올릴 때 — 나머지 칸은 그대로 */
    @Transactional
    public WorkTaskDto.View updateProgress(Long id, Integer progress) {
        WorkTaskAccess.Caller caller = access.requireCaller();
        WorkTask task = findInCompany(id, caller);
        requireCanEdit(task, caller);
        boolean wasDone = task.isDone();
        task.fill(task.getContent(), task.getImportance(), validProgress(progress), task.getDueDate());
        WorkTask saved = tasks.saveAndFlush(task);
        notifier.sendAfterCommit(finishedMessage(saved, wasDone, caller));
        return toView(saved, caller);
    }

    @Transactional
    public void delete(Long id) {
        WorkTaskAccess.Caller caller = access.requireCaller();
        WorkTask task = findInCompany(id, caller);
        if (!canDelete(task, caller)) {
            throw new CustomException("관리자나 등록한 사람만 지울 수 있습니다", HttpStatus.FORBIDDEN);
        }
        tasks.delete(task);
    }

    // ─── 권한 ───

    static boolean canEdit(WorkTask task, WorkTaskAccess.Caller caller) {
        return caller.admin() || caller.personId().equals(task.getCreatedById()) || task.isAssignee(caller.personId());
    }

    static boolean canDelete(WorkTask task, WorkTaskAccess.Caller caller) {
        return caller.admin() || caller.personId().equals(task.getCreatedById());
    }

    private static void requireCanEdit(WorkTask task, WorkTaskAccess.Caller caller) {
        if (!canEdit(task, caller)) {
            throw new CustomException("관리자·등록한 사람·담당자만 고칠 수 있습니다", HttpStatus.FORBIDDEN);
        }
    }

    private WorkTask findInCompany(Long id, WorkTaskAccess.Caller caller) {
        return tasks.findByIdAndCompanyId(id, caller.companyId())
                .orElseThrow(() -> new CustomException("업무를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
    }

    // ─── 입력 검사 ───

    private record Valid(String content, String importance, int progress, LocalDate dueDate) {
    }

    private static Valid validate(WorkTaskDto.Input input) {
        if (input == null) {
            throw bad("업무 내용을 보내 주세요");
        }
        String content = input.content() == null ? "" : input.content().strip();
        if (content.isEmpty()) {
            throw bad("업무 내용을 적어 주세요");
        }
        if (content.length() > CONTENT_MAX) {
            throw bad("업무 내용은 " + CONTENT_MAX + "자까지 적을 수 있습니다");
        }
        String importance = input.importance() == null ? "NORMAL" : input.importance().trim().toUpperCase();
        if (!IMPORTANCE.contains(importance)) {
            throw bad("중요도는 중요·보통·낮음 중에서 골라 주세요");
        }
        LocalDate due = input.dueDate();
        LocalDate today = LocalDate.now(SEOUL);
        if (due != null && (due.isBefore(today.minusYears(1)) || due.isAfter(today.plusYears(3)))) {
            throw bad("기한 날짜를 다시 확인해 주세요");
        }
        return new Valid(content, importance, validProgress(input.progress()), due);
    }

    private static int validProgress(Integer progress) {
        int p = progress == null ? 0 : progress;
        if (p < 0 || p > WorkTask.DONE) {
            throw bad("수행도는 0~100 사이로 적어 주세요");
        }
        return p;
    }

    private Map<String, String> assigneesOf(WorkTaskDto.Input input, WorkTaskAccess.Caller caller) {
        List<String> ids = input.assigneeIds() == null || input.assigneeIds().isEmpty()
                ? List.of(caller.personId())
                : input.assigneeIds();
        if (ids.size() > MAX_ASSIGNEES) {
            throw bad("담당자는 " + MAX_ASSIGNEES + "명까지 정할 수 있습니다");
        }
        Map<String, String> names = people.namesInCompany(ids, caller.companyId());
        if (names.isEmpty()) {
            throw bad("담당자를 한 명 이상 골라 주세요");
        }
        return names;
    }

    private static CustomException bad(String message) {
        return new CustomException(message, HttpStatus.BAD_REQUEST);
    }

    private static String clipName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.strip();
        return n.length() <= 50 ? n : n.substring(0, 50);
    }

    // ─── 알림 ───

    /** 새로 배정된 사람에게 — 자기 자신에게 맡긴 것은 알리지 않는다 */
    private static List<WorkTaskNotifier.Message> assignedMessages(WorkTask task, List<WorkTaskAssignee> added,
                                                                  WorkTaskAccess.Caller caller) {
        if (task.isDone()) {
            return List.of();
        }
        String who = caller.name() == null ? "" : caller.name() + ": ";
        String body = who + task.getContent() + WorkTaskReminderText.dueSuffix(task.getDueDate());
        return added.stream()
                .filter(a -> !a.getPersonId().equals(caller.personId()))
                .map(a -> new WorkTaskNotifier.Message(a.getPersonId(), a.getPersonName(),
                        "새 업무가 배정되었습니다", body, task.getId()))
                .toList();
    }

    /** 이번에 다 끝났으면 등록한 사람에게 — 본인이 끝낸 것은 알리지 않는다 */
    private static List<WorkTaskNotifier.Message> finishedMessage(WorkTask task, boolean wasDone,
                                                                 WorkTaskAccess.Caller caller) {
        if (wasDone || !task.isDone() || task.getCreatedById().equals(caller.personId())) {
            return List.of();
        }
        String who = caller.name() == null ? "담당자" : caller.name();
        return List.of(new WorkTaskNotifier.Message(task.getCreatedById(), task.getCreatedByName(),
                "업무를 마쳤습니다", who + "님이 '" + task.getContent() + "' 업무를 마쳤습니다", task.getId()));
    }

    // ─── 보기 ───

    private static final Comparator<WorkTask> DISPLAY_ORDER = Comparator
            .comparing(WorkTask::isDone)
            .thenComparing((a, b) -> a.isDone()
                    ? b.getCompletedAt().compareTo(a.getCompletedAt())
                    : WorkTaskReminderText.URGENCY.compare(a, b));

    private static WorkTaskDto.View toView(WorkTask t, WorkTaskAccess.Caller caller) {
        List<WorkTaskDto.Person> assignees = t.getAssignees().stream()
                .map(a -> new WorkTaskDto.Person(a.getPersonId(), a.getPersonName()))
                .toList();
        return new WorkTaskDto.View(t.getId(), t.getContent(), t.getImportance(), t.getProgress(), t.getDueDate(),
                assignees, new WorkTaskDto.Person(t.getCreatedById(), t.getCreatedByName()), t.getCreatedAt(),
                t.getUpdatedAt(), t.getCompletedAt(), canEdit(t, caller), canDelete(t, caller));
    }
}
