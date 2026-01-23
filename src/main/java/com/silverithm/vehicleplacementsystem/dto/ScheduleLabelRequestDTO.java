package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleLabelRequestDTO {

    @NotBlank(message = "라벨 이름을 입력해주세요")
    @Size(max = 50, message = "라벨 이름은 50자를 초과할 수 없습니다")
    private String name;

    @NotBlank(message = "색상 코드를 입력해주세요")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "올바른 색상 코드 형식이 아닙니다 (예: #3B82F6)")
    private String color;
}