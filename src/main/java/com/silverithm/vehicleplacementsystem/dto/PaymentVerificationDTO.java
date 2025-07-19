package com.silverithm.vehicleplacementsystem.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentVerificationDTO {
    private String paymentKey;
    private String orderId;
    private Long amount;

    public static PaymentVerificationDTO of(String paymentKey, String orderId, Long amount) {
        PaymentVerificationDTO dto = new PaymentVerificationDTO();
        dto.paymentKey = paymentKey;
        dto.orderId = orderId;
        dto.amount = amount;
        return dto;
    }
}