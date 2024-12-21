package com.silverithm.vehicleplacementsystem.entity;

public enum SubscriptionType {
    BASIC("BASIC"),
    ENTERPRISE("ENTERPRISE"),
    FREE("FREE");

    private final String value;

    SubscriptionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
