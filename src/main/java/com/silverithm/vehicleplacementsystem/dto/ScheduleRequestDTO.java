package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRequestDTO {

    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @Size(max = 5000, message = "내용은 5000자를 초과할 수 없습니다")
    private String content;

    @Builder.Default
    private String category = "OTHER"; // MEETING, EVENT, TRAINING, OTHER

    private Long labelId;

    @Size(max = 500, message = "장소는 500자를 초과할 수 없습니다")
    private String location;

    @NotNull(message = "시작 날짜를 입력해주세요")
    private LocalDate startDate;

    private LocalTime startTime;

    @NotNull(message = "종료 날짜를 입력해주세요")
    private LocalDate endDate;

    private LocalTime endTime;

    @Builder.Default
    private Boolean isAllDay = false;

    @Builder.Default
    private Boolean sendNotification = false;

    private List<Long> participantIds;
}