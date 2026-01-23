package com.silverithm.vehicleplacementsystem.entity;

public enum ScheduleCategory {
    MEETING("회의"),
    EVENT("행사"),
    TRAINING("교육"),
    OTHER("기타");

    private final String displayName;

    ScheduleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
