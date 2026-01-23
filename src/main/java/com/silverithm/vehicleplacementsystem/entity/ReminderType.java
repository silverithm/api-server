package com.silverithm.vehicleplacementsystem.entity;

public enum ReminderType {
    NONE("알림 없음"),
    TEN_MIN("10분 전"),
    THIRTY_MIN("30분 전"),
    ONE_HOUR("1시간 전"),
    ONE_DAY("1일 전");

    private final String displayName;

    ReminderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}