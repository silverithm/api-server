package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * 일정 색상. "#RRGGBB"면 그 색으로 설정, ""(빈 문자열)이면 색을 지운다(카테고리 기본색으로 폴백).
     * 필드 자체가 요청에 없으면(null) 기존 색을 그대로 둔다 — Schedule.update() 참고.
     */
    @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다")
    private String color;

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

    /** 담당자 id (미지정 시 null). members.id 또는 app_user.id — managerType으로 구분한다. */
    private Long managerId;

    /**
     * 담당자 종류. "MEMBER"(직원) | "ADMIN"(관리자/시설장).
     * 비어 있으면(null/blank) 구버전 클라이언트 호환을 위해 "MEMBER"로 본다.
     */
    private String managerType;
}