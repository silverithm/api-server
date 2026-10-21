package com.silverithm.vehicleplacementsystem.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 업무진행도 API의 요청·응답 모양 */
public final class WorkTaskDto {

    private WorkTaskDto() {
    }

    /**
     * 등록·수정 요청. 담당자는 채팅 식별자 목록("12", "admin_3") — 비면 등록한 사람 자신.
     * 이름은 받지 않는다 — 서버가 기관 명단에서 찾는다.
     */
    public record Input(String content, String importance, Integer progress, LocalDate dueDate,
                       List<String> assigneeIds) {
    }

    /** 수행도만 바꾸는 요청 — 목록에서 바로 올릴 때 */
    public record ProgressInput(Integer progress) {
    }

    public record Person(String id, String name) {
    }

    /**
     * @param canEdit 요청한 사람이 고칠 수 있는가(관리자·등록한 사람·담당자)
     * @param canDelete 요청한 사람이 지울 수 있는가(관리자·등록한 사람)
     */
    public record View(Long id, String content, String importance, int progress, LocalDate dueDate,
                       List<Person> assignees, Person createdBy, LocalDateTime createdAt,
                       LocalDateTime updatedAt, LocalDateTime completedAt, boolean canEdit, boolean canDelete) {
    }
}
