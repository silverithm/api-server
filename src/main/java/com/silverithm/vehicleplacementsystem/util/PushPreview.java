package com.silverithm.vehicleplacementsystem.util;

/**
 * 푸시 알림 본문 미리보기 — 긴 채팅 메시지(최대 5000자)를 그대로 알림 표에 저장하면 message 칸(1000자)을 넘어
 * "Data too long for column 'message'"로 알림 저장이 실패한다(운영 2026-10-21). 알림은 미리보기면 충분하므로 줄여서 넣는다.
 */
public final class PushPreview {

    /** 알림 본문 최대 글자 수(마지막 말줄임표 포함). 저장 칸 한도보다 넉넉히 작다 */
    public static final int MAX_CHARS = 300;

    private PushPreview() {
    }

    /** 한도를 넘으면 글자(코드 포인트) 단위로 잘라 말줄임표를 붙인다. null은 빈 문자열 */
    public static String of(String text) {
        if (text == null) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= MAX_CHARS) {
            return text;
        }
        int end = text.offsetByCodePoints(0, MAX_CHARS - 1);
        return text.substring(0, end) + "…";
    }
}
