package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 기본 일정 구분 설정 변경 요청. null인 필드는 건드리지 않는다(부분 수정).
 * 이름·색을 기본값 그대로 보내면 "커스텀 없음"으로 저장한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleCategorySettingRequestDTO {

    @Size(max = 50, message = "구분 이름은 50자 이내여야 합니다")
    private String name;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다")
    private String color;

    private Boolean hidden;
}
