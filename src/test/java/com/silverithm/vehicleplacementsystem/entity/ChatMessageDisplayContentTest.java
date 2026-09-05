package com.silverithm.vehicleplacementsystem.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대화 밖에서 메시지를 가리키는 한 줄 — 방 목록 미리보기, 푸시 알림, 알림함이 이 값을 쓴다.
 *
 * 동영상은 저장 타입이 FILE이라 예전에는 파일 이름이 그대로 나왔다. 앱이 압축하며 붙인
 * compressed_1757….mp4 같은 임시 이름이 잠금화면에 뜨던 이유다.
 */
@DisplayName("메시지 한 줄 요약(displayContent)")
class ChatMessageDisplayContentTest {

    private ChatMessage message(ChatMessage.MessageType type, String mimeType, String fileName) {
        return ChatMessage.builder()
                .type(type)
                .mimeType(mimeType)
                .fileName(fileName)
                .content(fileName)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("사진은 '사진'이라고 말한다 — 파일 이름을 드러내지 않는다")
    void image() {
        assertThat(message(ChatMessage.MessageType.IMAGE, "image/jpeg", "compressed_1757012345678.jpg")
                .getDisplayContent()).isEqualTo("사진");
    }

    @Test
    @DisplayName("동영상은 '동영상' — 저장 타입이 FILE이어도")
    void video() {
        assertThat(message(ChatMessage.MessageType.FILE, "video/mp4", "compressed_1757012345678.mp4")
                .getDisplayContent()).isEqualTo("동영상");
    }

    @Test
    @DisplayName("mimeType이 없는 옛 동영상도 확장자로 알아본다")
    void legacyVideoWithoutMime() {
        assertThat(message(ChatMessage.MessageType.FILE, null, "예전영상.mov")
                .getDisplayContent()).isEqualTo("동영상");
    }

    @Test
    @DisplayName("문서는 파일 이름 그대로 — 무슨 문서인지가 정보다")
    void document() {
        assertThat(message(ChatMessage.MessageType.FILE, "application/pdf", "9월 근무표.pdf")
                .getDisplayContent()).isEqualTo("9월 근무표.pdf");
    }

    @Test
    @DisplayName("녹음(오디오)은 파일로 둔다 — 화면에 재생기가 없다")
    void audioStaysFile() {
        assertThat(message(ChatMessage.MessageType.FILE, "audio/webm", "회의녹음.webm")
                .getDisplayContent()).isEqualTo("회의녹음.webm");
    }

    @Test
    @DisplayName("글은 내용 그대로")
    void text() {
        ChatMessage text = ChatMessage.builder()
                .type(ChatMessage.MessageType.TEXT)
                .content("오늘 3시에 뵙겠습니다")
                .isDeleted(false)
                .build();
        assertThat(text.getDisplayContent()).isEqualTo("오늘 3시에 뵙겠습니다");
    }

    @Test
    @DisplayName("지운 메시지는 종류와 무관하게 지웠다고 말한다")
    void deleted() {
        ChatMessage deleted = ChatMessage.builder()
                .type(ChatMessage.MessageType.IMAGE)
                .mimeType("image/jpeg")
                .fileName("사진.jpg")
                .isDeleted(true)
                .build();
        assertThat(deleted.getDisplayContent()).isEqualTo("삭제된 메시지입니다");
    }
}
