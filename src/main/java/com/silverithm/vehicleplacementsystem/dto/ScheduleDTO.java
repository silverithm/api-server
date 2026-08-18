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
    /** DB에 저장된 값 그대로(null 가능) — 폼 초기화에 쓰이므로 서버가 임의로 채우지 않는다. */
    private String color;
    /**
     * 구버전 클라이언트 호환용 shim. 항상 non-null이며 color에는 effectiveColor
     * (schedule.color → label.color → 카테고리 기본색 순 폴백)가 들어간다.
     * 실제 라벨이 있으면 그 라벨의 id/name을 유지하고, 없으면 id=null, name=""이다.
     */
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
    /** 담당자 (미지정 시 null) */
    private Long managerId;
    private String managerName;
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
                .color(schedule.getColor())
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
                .managerId(schedule.getManagerMemberId())
                .managerName(schedule.getManagerName())
                .authorId(schedule.getAuthorId())
                .authorName(schedule.getAuthorName())
                .companyId(schedule.getCompany().getId())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt());

        // 구버전 Flutter 앱은 schedule.label.color 하나만 읽는다. 이 필드가 비면
        // 앱이 고정 브랜드색으로 폴백해 카테고리 구분이 사라지므로 항상 non-null로 채운다.
        String effectiveColor = (schedule.getColor() != null && !schedule.getColor().isBlank())
                ? schedule.getColor()
                : (schedule.getLabel() != null
                        ? schedule.getLabel().getColor()
                        : schedule.getCategory().getDefaultColor());

        if (schedule.getLabel() != null) {
            ScheduleLabelDTO labelShim = ScheduleLabelDTO.fromEntity(schedule.getLabel());
            labelShim.setColor(effectiveColor);
            builder.label(labelShim);
        } else {
            builder.label(ScheduleLabelDTO.builder()
                    .id(null)
                    .name("")
                    .color(effectiveColor)
                    .build());
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