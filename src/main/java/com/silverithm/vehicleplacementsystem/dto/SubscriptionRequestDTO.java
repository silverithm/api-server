package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SubscriptionRequestDTO {
    private SubscriptionType planName;
    private SubscriptionBillingType billingType;
    private Integer amount;
}
