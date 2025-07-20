package com.silverithm.vehicleplacementsystem.entity;

public enum PaymentFailureReason {
    CARD_LIMIT_EXCEEDED("카드 한도 초과"),
    CARD_SUSPENDED("카드 정지"),
    INSUFFICIENT_BALANCE("잔액 부족"),
    INVALID_CARD("유효하지 않은 카드"),
    EXPIRED_CARD("카드 만료"),
    NETWORK_ERROR("네트워크 오류"),
    PAYMENT_GATEWAY_ERROR("결제 게이트웨이 오류"),
    OTHER("기타");

    private final String description;

    PaymentFailureReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}