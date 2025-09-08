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
    
    private String type;
}