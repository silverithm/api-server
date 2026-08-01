package com.silverithm.vehicleplacementsystem.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청자 IP 해석. 프록시(Nginx) 뒤에서 동작하므로 X-Forwarded-For의 첫 IP를 우선 사용한다.
 * (Nginx가 X-Forwarded-For를 세팅하므로 외부에서 임의 스푸핑된 값은 프록시 체인 뒤로 밀린다)
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
