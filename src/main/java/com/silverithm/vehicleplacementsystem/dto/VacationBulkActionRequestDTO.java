package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBulkActionRequestDTO {
    
    @NotEmpty(message = "휴무 ID 목록은 필수입니다")
    private List<Long> vacationIds;
    
    private String adminComment;
}