package com.silverithm.vehicleplacementsystem.entity;

public enum ScheduleCategory {
    // 색을 안 고른 일정이 폴백하는 카테고리 기본색. 웹(SCHEDULE_CATEGORY_COLORS)·앱과 반드시 동일해야
    // 하므로 하드코딩된 hex는 이 enum에만 둔다 — 다른 곳(ScheduleDTO 등)은 이 값을 참조만 한다.
    MEETING("회의", "#3B82F6"),
    EVENT("행사", "#EC4899"),
    TRAINING("교육", "#8B5CF6"),
    OTHER("기타", "#14B8A6");

    private final String displayName;
    private final String defaultColor;

    ScheduleCategory(String displayName, String defaultColor) {
        this.displayName = displayName;
        this.defaultColor = defaultColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultColor() {
        return defaultColor;
    }
}
