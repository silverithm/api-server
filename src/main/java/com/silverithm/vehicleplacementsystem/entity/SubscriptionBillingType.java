package com.silverithm.vehicleplacementsystem.entity;

import java.time.LocalDateTime;

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

    public static LocalDateTime calculateEndDate(SubscriptionBillingType billingType) {
        return billingType == SubscriptionBillingType.MONTHLY ? LocalDateTime.now().plusMonths(1)
                : LocalDateTime.now().plusYears(1);
    }

    public static LocalDateTime extendEndDate(SubscriptionBillingType billingType, LocalDateTime currentEndDate) {
        return billingType == SubscriptionBillingType.MONTHLY ? currentEndDate.plusMonths(1)
                : currentEndDate.plusYears(1);
    }

}