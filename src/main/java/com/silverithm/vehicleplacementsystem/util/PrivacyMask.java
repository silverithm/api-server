package com.silverithm.vehicleplacementsystem.util;

/**
 * 로그에 남기는 개인정보 마스킹.
 *
 * <p>운영 로그는 그 자체가 보관·접근통제 대상이 되므로 성명·이메일·연락처를 평문으로 남기지 않는다.
 * 장애 추적에 필요한 최소한의 식별성(앞 글자, 도메인)만 유지한다.
 */
public final class PrivacyMask {

    private static final String EMPTY = "-";

    private PrivacyMask() {
    }

    /** 이메일: {@code hong@example.com} → {@code ho**@example.com} */
    public static String email(String email) {
        if (isBlank(email)) {
            return EMPTY;
        }

        int at = email.indexOf('@');
        if (at <= 0) {
            return name(email);
        }

        String local = email.substring(0, at);
        String domain = email.substring(at);

        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.substring(0, 2) + "*".repeat(local.length() - 2) + domain;
    }

    /** 성명/아이디: {@code 홍길동} → {@code 홍*동}, {@code 김철} → {@code 김*} */
    public static String name(String name) {
        if (isBlank(name)) {
            return EMPTY;
        }

        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0)
                + "*".repeat(trimmed.length() - 2)
                + trimmed.charAt(trimmed.length() - 1);
    }

    /** 연락처: {@code 010-1234-5678} → {@code 010-****-5678} */
    public static String phone(String phone) {
        if (isBlank(phone)) {
            return EMPTY;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return "*".repeat(digits.length());
        }

        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-****-" + tail;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
