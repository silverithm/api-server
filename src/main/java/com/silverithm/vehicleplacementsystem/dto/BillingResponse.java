package com.silverithm.vehicleplacementsystem.dto;

public record BillingResponse(
        String mId,
        String customerKey,
        String authenticatedAt,
        String method,
        String billingKey,
        Card card
) {
    public record Card(
            String issuerCode,
            String acquirerCode,
            String number,
            String cardType,
            String ownerType
    ) {}
}