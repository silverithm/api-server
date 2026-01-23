package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ScheduleParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleParticipantDTO {
    private Long id;
    private Long scheduleId;
    private Long memberId;
    private String memberName;
    private String status;
    private LocalDateTime respondedAt;
    private LocalDateTime createdAt;

    public static ScheduleParticipantDTO fromEntity(ScheduleParticipant participant) {
        return ScheduleParticipantDTO.builder()
                .id(participant.getId())
                .scheduleId(participant.getSchedule().getId())
                .memberId(participant.getMemberId())
                .memberName(participant.getMemberName())
                .status(participant.getStatus().name())
                .respondedAt(participant.getRespondedAt())
                .createdAt(participant.getCreatedAt())
                .build();
    }
}
