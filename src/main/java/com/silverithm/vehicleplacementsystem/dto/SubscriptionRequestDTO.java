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
    private String customerKey;
    private String authKey;
    private String orderName;
    private String customerEmail;
    private String customerName;
    private int taxFreeAmount;

    public static SubscriptionRequestDTO of(SubscriptionType planName, SubscriptionBillingType billingType,
                                            Integer amount, String customerKey, String authKey, String orderName,
                                            String customerEmail, String customerName, int taxFreeAmount) {
        SubscriptionRequestDTO dto = new SubscriptionRequestDTO();
        dto.planName = planName;
        dto.billingType = billingType;
        dto.amount = amount;
        dto.customerKey = customerKey;
        dto.authKey = authKey;
        dto.orderName = orderName;
        dto.customerEmail = customerEmail;
        dto.customerName = customerName;
        dto.taxFreeAmount = taxFreeAmount;
        return dto;
    }
}
