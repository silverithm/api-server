package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ScheduleTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleTaskDTO {
    private Long id;
    private Long scheduleId;
    private String content;
    private Long assigneeMemberId;
    private String assigneeName;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private String completedById;
    private String completedByName;
    private String createdById;
    private String createdByName;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    // '내 할 일' 목록에서 어떤 일정의 업무인지 보여주기 위한 부가 정보
    private String scheduleTitle;
    private LocalDate scheduleStartDate;
    private LocalDate scheduleEndDate;

    public static ScheduleTaskDTO fromEntity(ScheduleTask task) {
        return ScheduleTaskDTO.builder()
                .id(task.getId())
                .scheduleId(task.getSchedule().getId())
                .content(task.getContent())
                .assigneeMemberId(task.getAssigneeMemberId())
                .assigneeName(task.getAssigneeName())
                .isCompleted(Boolean.TRUE.equals(task.getIsCompleted()))
                .completedAt(task.getCompletedAt())
                .completedById(task.getCompletedById())
                .completedByName(task.getCompletedByName())
                .createdById(task.getCreatedById())
                .createdByName(task.getCreatedByName())
                .sortOrder(task.getSortOrder())
                .createdAt(task.getCreatedAt())
                .build();
    }

    /** '내 할 일'처럼 일정 정보가 함께 필요한 경우 */
    public static ScheduleTaskDTO fromEntityWithSchedule(ScheduleTask task) {
        ScheduleTaskDTO dto = fromEntity(task);
        dto.setScheduleTitle(task.getSchedule().getTitle());
        dto.setScheduleStartDate(task.getSchedule().getStartDate());
        dto.setScheduleEndDate(task.getSchedule().getEndDate());
        return dto;
    }
}
