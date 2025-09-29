package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminVacationCreateRequestDTO {

    @NotNull(message = "직원 ID는 필수입니다")
    private Long memberId;

    @NotNull(message = "휴무 날짜는 필수입니다")
    private LocalDate date;

    private String reason;

    @NotNull(message = "휴무 기간은 필수입니다")
    private VacationRequest.VacationDuration duration;

    private String type;  // regular(일반) or mandatory(필수)

    // 새로 추가된 필드들
    @Builder.Default
    private Boolean useAnnualLeave = true;  // 연차 사용 여부 (기본값: true)

    private String vacationType;  // 휴무 유형 (personal, sick, emergency, family 등)

    @Builder.Default
    private Boolean reasonRequired = false;  // 사유 필수 여부 (mandatory일 때 true)
}