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
    private List<ScheduleParticipantDTO> participants;
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
                .authorId(schedule.getAuthorId())
                .authorName(schedule.getAuthorName())
                .companyId(schedule.getCompany().getId())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt());

        if (schedule.getLabel() != null) {
            builder.label(ScheduleLabelDTO.fromEntity(schedule.getLabel()));
        }

        if (schedule.getParticipants() != null && !schedule.getParticipants().isEmpty()) {
            builder.participants(schedule.getParticipants().stream()
                    .map(ScheduleParticipantDTO::fromEntity)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }
}