package com.silverithm.vehicleplacementsystem.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("채팅 첨부의 화면용 종류(mediaType) 판정")
class ChatMediaTypeTest {

    @Nested
    @DisplayName("지금 올라오는 파일")
    class Fresh {

        @Test
        @DisplayName("동영상은 저장 타입이 FILE이어도 VIDEO로 내려간다")
        void videoMimeBecomesVideo() {
            assertThat(ChatMediaType.resolve("FILE", "video/mp4", "회의.mp4")).isEqualTo("VIDEO");
            assertThat(ChatMediaType.resolve("FILE", "video/quicktime", "IMG_0001.MOV")).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("사진은 그대로 IMAGE")
        void imageStaysImage() {
            assertThat(ChatMediaType.resolve("IMAGE", "image/jpeg", "사진.jpg")).isEqualTo("IMAGE");
        }

        @Test
        @DisplayName("문서는 FILE")
        void documentStaysFile() {
            assertThat(ChatMediaType.resolve("FILE", "application/pdf", "계약서.pdf")).isEqualTo("FILE");
        }
    }

    @Nested
    @DisplayName("이미 FILE로 쌓여 있는 옛 메시지 — 백필 없이 살아나야 한다")
    class Legacy {

        @Test
        @DisplayName("mimeType이 비어 있으면 파일명 확장자로 동영상을 알아본다")
        void nullMimeFallsBackToExtension() {
            assertThat(ChatMediaType.resolve("FILE", null, "지난주 행사.mp4")).isEqualTo("VIDEO");
            assertThat(ChatMediaType.resolve("FILE", null, "지난주 행사.mov")).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("구버전 앱이 보낸 application/octet-stream도 확장자로 구제된다")
        void octetStreamFallsBackToExtension() {
            assertThat(ChatMediaType.resolve("FILE", "application/octet-stream", "a.mp4")).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("확장자도 파일명도 없으면 저장된 타입 그대로 둔다")
        void unknownStaysAsStored() {
            assertThat(ChatMediaType.resolve("FILE", null, null)).isEqualTo("FILE");
            assertThat(ChatMediaType.resolve("FILE", null, "첨부")).isEqualTo("FILE");
        }
    }

    @Nested
    @DisplayName("헷갈리기 쉬운 것들")
    class Tricky {

        @Test
        @DisplayName("회의 녹음(webm/m4a)은 동영상이 아니다")
        void meetingAudioIsNotVideo() {
            assertThat(ChatMediaType.resolve("FILE", "audio/webm", "회의록.webm")).isEqualTo("FILE");
            assertThat(ChatMediaType.resolve("FILE", "audio/mp4", "녹음.m4a")).isEqualTo("FILE");
        }

        @Test
        @DisplayName("파일명이 .mp4라고 우겨도 내용이 이미지면 IMAGE를 지킨다")
        void contentTypeWinsOverExtension() {
            assertThat(ChatMediaType.resolve("IMAGE", "image/jpeg", "이상한이름.mp4")).isEqualTo("IMAGE");
        }

        @Test
        @DisplayName("확장자 대소문자를 가리지 않는다")
        void extensionIsCaseInsensitive() {
            assertThat(ChatMediaType.resolve("FILE", null, "VIDEO.MP4")).isEqualTo("VIDEO");
        }

        @Test
        @DisplayName("첨부가 아닌 메시지는 null — 글·시스템 메시지에 미디어 종류는 없다")
        void nonAttachmentIsNull() {
            assertThat(ChatMediaType.resolve("TEXT", null, null)).isNull();
            assertThat(ChatMediaType.resolve("SYSTEM", null, null)).isNull();
        }
    }
}
