package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import org.springframework.http.HttpStatus;

/**
 * 플랜별 판매 가격의 서버 측 단일 진실(single source of truth).
 *
 * 결제 금액은 절대 클라이언트 입력을 신뢰하지 않는다 — 과거에는 프론트가 보낸 amount를
 * 그대로 토스에 청구해 devtools로 금액을 조작할 수 있었다. 결제가 일어나는 모든 경로는
 * 반드시 {@link #requiredAmount}로 금액을 강제해야 한다.
 *
 * 여기에 정의되지 않은 (플랜, 결제주기) 조합은 판매하지 않는 것이며 결제 요청을 거부한다.
 * 새 플랜을 판매하려면 이 표에 가격을 추가하는 것이 유일한 방법이다.
 */
public final class SubscriptionPricing {

    private SubscriptionPricing() {
    }

    public static int requiredAmount(SubscriptionType planName, SubscriptionBillingType billingType) {
        if (planName == SubscriptionType.BASIC && billingType == SubscriptionBillingType.MONTHLY) {
            return 9900;
        }
        throw new CustomException(
                "판매 중인 플랜이 아닙니다: " + planName + "/" + billingType,
                HttpStatus.BAD_REQUEST);
    }
}
