package com.silverithm.vehicleplacementsystem.util;

import com.silverithm.vehicleplacementsystem.entity.AppUser;

/**
 * 관리자 계정을 사람에게 보여줄 때의 표기 규칙.
 *
 * 결재선 후보와 채팅 참가자 목록이 서로 다르게 보이면 같은 사람이 다른 사람처럼 읽히므로
 * 한곳에 모아둔다. 직책을 정해두지 않은 계정은 예전처럼 '관리자'로 보인다.
 */
public final class AdminDisplay {

    public static final String DEFAULT_POSITION = "관리자";

    private AdminDisplay() {
    }

    public static String position(AppUser appUser) {
        if (appUser == null) {
            return DEFAULT_POSITION;
        }
        String position = appUser.getPosition();
        return (position == null || position.isBlank()) ? DEFAULT_POSITION : position;
    }
}
