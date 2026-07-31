package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Schedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleDTO {
    private Long id;
    private String title;
    private String content;
    private String category;
    private String categoryDisplayName;
    private ScheduleLabelDTO label;
    private String location;
    private LocalDate startDate;
    private LocalTime startTime;
    private LocalDate endDate;
    private LocalTime endTime;
    private Boolean isAllDay;
    private Boolean sendNotification;
    private Boolean isCompleted;
    private LocalDateTime completedAt;
    private String completedById;
    private String completedByName;
    private List<ScheduleParticipantDTO> participants;
    private List<ScheduleTaskDTO> tasks;
    /** 할 일 총 개수 */
    private Integer taskTotal;
    /** 완료된 할 일 개수 */
    private Integer taskCompleted;
    private String authorId;
    private String authorName;
    private Long companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScheduleDTO fromEntity(Schedule schedule) {
        ScheduleDTOBuilder builder = ScheduleDTO.builder()
                .id(schedule.getId())
                .title(schedule.getTitle())
                .content(schedule.getContent())
                .category(schedule.getCategory().name())
                .categoryDisplayName(schedule.getCategory().getDisplayName())
                .location(schedule.getLocation())
                .startDate(schedule.getStartDate())
                .startTime(schedule.getStartTime())
                .endDate(schedule.getEndDate())
                .endTime(schedule.getEndTime())
                .isAllDay(schedule.getIsAllDay())
                .sendNotification(schedule.getSendNotification())
                .isCompleted(Boolean.TRUE.equals(schedule.getIsCompleted()))
                .completedAt(schedule.getCompletedAt())
                .completedById(schedule.getCompletedById())
                .completedByName(schedule.getCompletedByName())
                .authorId(schedule.getAuthorId())
                .authorName(schedule.getAuthorName())
                .companyId(schedule.getCompany().getId())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt());

        if (schedule.getLabel() != null) {
            builder.label(ScheduleLabelDTO.fromEntity(schedule.getLabel()));
        }

        if (schedule.getTasks() != null) {
            List<ScheduleTaskDTO> tasks = schedule.getTasks().stream()
                    .sorted(java.util.Comparator
                            .comparing(com.silverithm.vehicleplacementsystem.entity.ScheduleTask::getSortOrder,
                                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                            .thenComparing(com.silverithm.vehicleplacementsystem.entity.ScheduleTask::getId))
                    .map(ScheduleTaskDTO::fromEntity)
                    .collect(Collectors.toList());
            builder.tasks(tasks);
            builder.taskTotal(tasks.size());
            builder.taskCompleted((int) tasks.stream().filter(t -> Boolean.TRUE.equals(t.getIsCompleted())).count());
        } else {
            builder.taskTotal(0);
            builder.taskCompleted(0);
        }

        if (schedule.getParticipants() != null && !schedule.getParticipants().isEmpty()) {
            builder.participants(schedule.getParticipants().stream()
                    .map(ScheduleParticipantDTO::fromEntity)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }
}