package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업무진행도의 업무 한 건 — 기관 전체가 같은 목록을 본다(관리자·종사자 모두 동일).
 *
 * <p>수행도가 100이 되면 완료 시각을 남기고, 100 아래로 내리면 다시 진행 중이 된다.
 * 진행 중인 동안은 담당자에게 매일 알림이 간다({@code WorkTaskReminderScheduler}).
 */
@Entity
@Table(name = "work_tasks", indexes = @Index(name = "idx_work_tasks_company_completed",
        columnList = "company_id, completed_at"))
@Getter
@NoArgsConstructor
public class WorkTask {

    public static final int DONE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 300)
    private String content;

    /** HIGH | NORMAL | LOW */
    @Column(nullable = false, length = 10)
    private String importance;

    @Column(nullable = false)
    private Integer progress;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 채팅 식별자 — 직원 "12", 관리자 "admin_3" */
    @Column(name = "created_by_id", nullable = false, length = 40)
    private String createdById;

    @Column(name = "created_by_name", length = 50)
    private String createdByName;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "workTask", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<WorkTaskAssignee> assignees = new ArrayList<>();

    public WorkTask(Company company, String createdById, String createdByName) {
        this.company = company;
        this.createdById = createdById;
        this.createdByName = createdByName;
        this.progress = 0;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void fill(String content, String importance, int progress, LocalDate dueDate) {
        this.content = content;
        this.importance = importance;
        this.dueDate = dueDate;
        setProgress(progress);
    }

    /** 100이 되는 순간 완료 시각을 남기고, 100 아래로 내리면 지운다 */
    private void setProgress(int progress) {
        boolean wasDone = isDone();
        this.progress = progress;
        if (isDone() && !wasDone) {
            completedAt = LocalDateTime.now();
        } else if (!isDone()) {
            completedAt = null;
        }
    }

    public boolean isDone() {
        return progress != null && progress >= DONE;
    }

    public boolean isAssignee(String personId) {
        return personId != null && assignees.stream().anyMatch(a -> personId.equals(a.getPersonId()));
    }

    /**
     * 담당자를 맞춘다 — 빠진 사람만 지우고, 남는 사람은 그대로 두고(이름만 갱신), 새 사람만 더한다.
     * 통째로 지우고 다시 넣으면 Hibernate가 INSERT를 DELETE보다 먼저 내보내 유니크 키에 걸린다
     * (2026-10-09 결재 열람자 사고와 같은 모양).
     *
     * @return 새로 더해진 담당자 — 이 사람들에게만 배정 알림을 보낸다
     */
    public List<WorkTaskAssignee> reconcileAssignees(Map<String, String> wantedIdToName) {
        Map<String, String> wanted = new LinkedHashMap<>(wantedIdToName);
        assignees.removeIf(a -> {
            String name = wanted.remove(a.getPersonId());
            if (name == null) {
                return true;
            }
            a.rename(name);
            return false;
        });
        List<WorkTaskAssignee> added = new ArrayList<>();
        wanted.forEach((id, name) -> {
            WorkTaskAssignee assignee = new WorkTaskAssignee(this, id, name);
            assignees.add(assignee);
            added.add(assignee);
        });
        return added;
    }
}
