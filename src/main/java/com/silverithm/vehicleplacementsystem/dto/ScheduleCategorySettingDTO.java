package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ScheduleCategory;
import com.silverithm.vehicleplacementsystem.entity.ScheduleCategorySetting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 기본 일정 구분의 기관별 최종 상태 — 설정과 enum 기본값을 머지해 내려준다.
 * 프론트는 name/color/hidden만 쓰면 되고, 되돌리기 UI를 위해 기본값도 함께 준다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleCategorySettingDTO {
    private String category;
    private String name;
    private String color;
    private boolean hidden;
    private String defaultName;
    private String defaultColor;
    /** 이름·색·숨김 중 하나라도 기본에서 바뀌었는지 (되돌리기 버튼 노출용) */
    private boolean customized;

    public static ScheduleCategorySettingDTO of(ScheduleCategory category, ScheduleCategorySetting setting) {
        boolean customized = setting != null
                && ((setting.getDisplayName() != null && !setting.getDisplayName().isBlank())
                        || (setting.getColor() != null && !setting.getColor().isBlank())
                        || setting.isHidden());
        return ScheduleCategorySettingDTO.builder()
                .category(category.name())
                .name(setting != null ? setting.effectiveName() : category.getDisplayName())
                .color(setting != null ? setting.effectiveColor() : category.getDefaultColor())
                .hidden(setting != null && setting.isHidden())
                .defaultName(category.getDisplayName())
                .defaultColor(category.getDefaultColor())
                .customized(customized)
                .build();
    }
}
