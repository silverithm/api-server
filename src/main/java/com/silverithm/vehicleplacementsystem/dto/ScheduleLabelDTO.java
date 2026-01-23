package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ScheduleLabel;
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
public class ScheduleLabelDTO {
    private Long id;
    private String name;
    private String color;
    private Long companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScheduleLabelDTO fromEntity(ScheduleLabel label) {
        return ScheduleLabelDTO.builder()
                .id(label.getId())
                .name(label.getName())
                .color(label.getColor())
                .companyId(label.getCompany().getId())
                .createdAt(label.getCreatedAt())
                .updatedAt(label.getUpdatedAt())
                .build();
    }
}