package com.silverithm.vehicleplacementsystem.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushPreviewTest {

    @Test
    void shortTextIsKept() {
        assertThat(PushPreview.of("안녕하세요")).isEqualTo("안녕하세요");
        assertThat(PushPreview.of("")).isEmpty();
        assertThat(PushPreview.of(null)).isEmpty();
    }

    @Test
    void textAtLimitIsKept() {
        String exact = "가".repeat(PushPreview.MAX_CHARS);
        assertThat(PushPreview.of(exact)).isEqualTo(exact);
    }

    @Test
    void longTextIsCutWithEllipsis() {
        // 채팅 메시지는 최대 5000자 — 알림 표의 message 칸(1000자)을 넘지 않아야 한다
        String longText = "나".repeat(5000);
        String out = PushPreview.of(longText);
        assertThat(out.codePointCount(0, out.length())).isEqualTo(PushPreview.MAX_CHARS);
        assertThat(out).endsWith("…");
        assertThat(out.length()).isLessThanOrEqualTo(1000);
    }

    @Test
    void emojiIsNotSplitInTheMiddle() {
        // 이모지는 UTF-16 두 칸이라 글자 수가 아니라 코드 포인트로 자른다
        String text = "😀".repeat(400);
        String out = PushPreview.of(text);
        assertThat(out.codePointCount(0, out.length())).isEqualTo(PushPreview.MAX_CHARS);
        assertThat(out.substring(0, out.length() - 1)).doesNotContain("�");
        assertThat(Character.isHighSurrogate(out.charAt(out.length() - 2))).isFalse();
    }
}
