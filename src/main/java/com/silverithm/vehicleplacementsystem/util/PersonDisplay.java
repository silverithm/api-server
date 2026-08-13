package com.silverithm.vehicleplacementsystem.util;

/**
 * 알림처럼 좁은 자리에 사람을 적을 때의 표기.
 *
 * 이름만 적으면 같은 이름이 여럿일 때 누구인지 알 수 없고, 관리자가 알림만 보고
 * 요양보호사인지 사무직인지 판단하지 못한다. 그래서 직책이 있으면 이름 뒤에 괄호로 붙인다.
 * 직책이 없으면 예전처럼 이름만 나온다.
 */
public final class PersonDisplay {

    private PersonDisplay() {
    }

    /** "김하늘(시설장)" — 직책이 없으면 "김하늘" */
    public static String withPosition(String name, String position) {
        String safeName = name == null ? "" : name.trim();
        if (position == null || position.isBlank()) {
            return safeName;
        }
        return safeName + "(" + position.trim() + ")";
    }
}
