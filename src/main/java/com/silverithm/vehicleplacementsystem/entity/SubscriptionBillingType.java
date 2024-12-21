package com.silverithm.vehicleplacementsystem.entity;

public enum SubscriptionBillingType {
    FREE("FREE"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    private final String value;

    SubscriptionBillingType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}