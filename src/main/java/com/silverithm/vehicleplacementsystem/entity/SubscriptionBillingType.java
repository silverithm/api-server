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

    /**
     * 앵커 데이(최초 가입일의 '일') 보존 연장.
     * plusMonths만 쓰면 1/31 가입자가 2월에 28일로 당겨진 뒤 영구히 28일로 굳는다(주기 드리프트).
     * 매번 가입일의 '일'로 복원을 시도하되, 그 달에 없는 날짜면 말일로 클램프한다.
     * 예) 앵커 31일: 1/31 → 2/28 → 3/31 → 4/30 → 5/31 ...
     */
    public static LocalDateTime extendEndDate(SubscriptionBillingType billingType, LocalDateTime base, int anchorDay) {
        LocalDateTime next = billingType == SubscriptionBillingType.MONTHLY ? base.plusMonths(1) : base.plusYears(1);
        int day = Math.min(anchorDay, next.toLocalDate().lengthOfMonth());
        return next.withDayOfMonth(day);
    }

}