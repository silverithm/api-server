package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 업무 담당자 한 사람. 이름은 등록·수정할 때 서버가 명단에서 찾아 적는다(요청 값을 믿지 않는다). */
@Entity
// V1.109.0의 유니크 키를 엔티티에도 적어 둔다 — 테스트 DB(H2)에도 같은 제약이 걸리게
@Table(name = "work_task_assignees",
        uniqueConstraints = @UniqueConstraint(name = "uk_work_task_assignee", columnNames = {"work_task_id", "person_id"}),
        indexes = @Index(name = "idx_work_task_assignees_person", columnList = "person_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkTaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_task_id", nullable = false)
    private WorkTask workTask;

    /** 채팅 식별자 — 직원 "12", 관리자 "admin_3" */
    @Column(name = "person_id", nullable = false, length = 40)
    private String personId;

    @Column(name = "person_name", nullable = false, length = 50)
    private String personName;

    WorkTaskAssignee(WorkTask workTask, String personId, String personName) {
        this.workTask = workTask;
        this.personId = personId;
        this.personName = personName;
    }

    void rename(String personName) {
        this.personName = personName;
    }
}
