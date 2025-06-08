package com.silverithm.vehicleplacementsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VacationCreateRequestDTO {

    @NotBlank(message = "사용자 이름은 필수입니다")
    private String userName;

    @NotNull(message = "날짜는 필수입니다")
    private LocalDate date;

    private String reason;

    @NotBlank(message = "직원 역할은 필수입니다")
    private String role;

    private String type;

    private String userId;
} 