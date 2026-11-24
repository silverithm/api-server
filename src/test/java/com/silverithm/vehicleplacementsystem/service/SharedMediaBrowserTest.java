package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("주고받은 파일 — 종류·검색어·달로 거르고 한 쪽씩")
class SharedMediaBrowserTest {

    private static ChatMessage msg(long id, ChatMessage.MessageType type, String mime, String fileName,
                                   String sender, LocalDateTime at) {
        return ChatMessage.builder()
                .id(id)
                .type(type)
                .mimeType(mime)
                .fileName(fileName)
                .senderName(sender)
                .createdAt(at)
                .build();
    }

    private static ChatMessage photo(long id, String sender, LocalDateTime at) {
        return msg(id, ChatMessage.MessageType.IMAGE, "image/jpeg", "img_" + id + ".jpg", sender, at);
    }

    /** 11월 사진 3장·동영상 1개·문서 1개, 10월 사진 2장·문서 1개 — 일부러 섞어서 넣는다 */
    private static List<ChatMessage> room() {
        List<ChatMessage> all = new ArrayList<>();
        all.add(photo(5, "박선영", LocalDateTime.of(2026, 10, 30, 9, 0)));
        all.add(photo(11, "김민지", LocalDateTime.of(2026, 11, 24, 18, 0)));
        all.add(msg(9, ChatMessage.MessageType.FILE, "video/mp4", "행사.mp4", "박선영",
                LocalDateTime.of(2026, 11, 20, 10, 0)));
        all.add(photo(10, "박선영", LocalDateTime.of(2026, 11, 22, 8, 0)));
        // 옛 동영상: mimeType 없이 FILE로 저장 — 확장자로 동영상이 된다
        all.add(msg(8, ChatMessage.MessageType.FILE, null, "IMG_0001.MOV", "김민지",
                LocalDateTime.of(2026, 11, 3, 10, 0)));
        all.add(msg(7, ChatMessage.MessageType.FILE, "application/pdf", "11월 근무표.pdf", "김관리",
                LocalDateTime.of(2026, 11, 2, 10, 0)));
        all.add(photo(4, "김민지", LocalDateTime.of(2026, 10, 12, 9, 0)));
        all.add(msg(3, ChatMessage.MessageType.FILE, "application/x-hwp", "10월 교육 자료.hwp", "김관리",
                LocalDateTime.of(2026, 10, 2, 9, 0)));
        // 첨부가 아닌 글은 섞여 들어와도 셈에서 빠진다
        all.add(msg(2, ChatMessage.MessageType.TEXT, null, null, "김관리", LocalDateTime.of(2026, 10, 1, 9, 0)));
        return all;
    }

    private static List<Long> ids(SharedMediaBrowser.Page page) {
        return page.items().stream().map(ChatMessage::getId).toList();
    }

    @Test
    @DisplayName("종류 없이 부르면 최신순으로 모두, 탭 숫자는 종류별")
    void allNewestFirstWithCounts() {
        SharedMediaBrowser.Page page = SharedMediaBrowser.browse(room(), null, null, null, null, 60);

        assertThat(ids(page)).containsExactly(11L, 10L, 9L, 8L, 7L, 5L, 4L, 3L);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.counts()).containsEntry("IMAGE", 4L).containsEntry("VIDEO", 2L).containsEntry("FILE", 2L);
    }

    @Test
    @DisplayName("사진만·동영상만·파일만 — 옛 .MOV도 동영상으로 간다")
    void filtersByKind() {
        assertThat(ids(SharedMediaBrowser.browse(room(), "IMAGE", null, null, null, 60)))
                .containsExactly(11L, 10L, 5L, 4L);
        assertThat(ids(SharedMediaBrowser.browse(room(), "photo", null, null, null, 60)))
                .containsExactly(11L, 10L, 5L, 4L);
        assertThat(ids(SharedMediaBrowser.browse(room(), "VIDEO", null, null, null, 60)))
                .containsExactly(9L, 8L);
        assertThat(ids(SharedMediaBrowser.browse(room(), "file", null, null, null, 60)))
                .containsExactly(7L, 3L);
    }

    @Test
    @DisplayName("한 쪽씩 자르고, beforeId 다음부터 이어 준다")
    void pagesWithCursor() {
        SharedMediaBrowser.Page first = SharedMediaBrowser.browse(room(), null, null, null, null, 3);
        assertThat(ids(first)).containsExactly(11L, 10L, 9L);
        assertThat(first.hasMore()).isTrue();

        SharedMediaBrowser.Page second = SharedMediaBrowser.browse(room(), null, null, 9L, null, 3);
        assertThat(ids(second)).containsExactly(8L, 7L, 5L);
        assertThat(second.hasMore()).isTrue();

        SharedMediaBrowser.Page last = SharedMediaBrowser.browse(room(), null, null, 5L, null, 3);
        assertThat(ids(last)).containsExactly(4L, 3L);
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    @DisplayName("이어 받을 기준 항목이 그 사이 지워졌으면 id로 이어 간다")
    void cursorFallsBackToIdWhenAnchorGone() {
        SharedMediaBrowser.Page page = SharedMediaBrowser.browse(room(), null, null, 6L, null, 60);
        assertThat(ids(page)).containsExactly(5L, 4L, 3L);
    }

    @Test
    @DisplayName("검색어는 파일 이름과 보낸 사람 이름에서 찾고, 탭 숫자도 검색 결과 기준")
    void searchesFileNameAndSender() {
        SharedMediaBrowser.Page byName = SharedMediaBrowser.browse(room(), null, "근무표", null, null, 60);
        assertThat(ids(byName)).containsExactly(7L);

        SharedMediaBrowser.Page bySender = SharedMediaBrowser.browse(room(), null, " 박선영 ", null, null, 60);
        assertThat(ids(bySender)).containsExactly(10L, 9L, 5L);
        assertThat(bySender.counts()).containsEntry("IMAGE", 2L).containsEntry("VIDEO", 1L).containsEntry("FILE", 0L);

        SharedMediaBrowser.Page caseless = SharedMediaBrowser.browse(room(), null, "img_0001", null, null, 60);
        assertThat(ids(caseless)).containsExactly(8L);
    }

    @Test
    @DisplayName("달 목록은 고른 종류 기준 최신 달 먼저, month로 그 달부터 시작한다")
    void monthsAndJump() {
        SharedMediaBrowser.Page all = SharedMediaBrowser.browse(room(), null, null, null, null, 60);
        assertThat(all.months()).extracting(SharedMediaBrowser.MonthCount::month).containsExactly("2026-11", "2026-10");
        assertThat(all.months()).extracting(SharedMediaBrowser.MonthCount::count).containsExactly(5L, 3L);

        SharedMediaBrowser.Page october = SharedMediaBrowser.browse(room(), "IMAGE", null, null, "2026-10", 60);
        assertThat(ids(october)).containsExactly(5L, 4L);

        // 항목이 없는 달을 고르면 그보다 오래된 것부터
        SharedMediaBrowser.Page gap = SharedMediaBrowser.browse(room(), "FILE", null, null, "2026-10", 60);
        assertThat(ids(gap)).containsExactly(3L);

        // 모르는 형식은 무시하고 처음부터
        assertThat(ids(SharedMediaBrowser.browse(room(), "FILE", null, null, "11월", 60))).containsExactly(7L, 3L);
    }

    @Test
    @DisplayName("쪽 크기는 1~200으로 묶는다")
    void clampsSize() {
        assertThat(SharedMediaBrowser.browse(room(), null, null, null, null, 0).items()).hasSize(1);
        List<ChatMessage> many = new ArrayList<>();
        for (int i = 1; i <= 250; i++) {
            many.add(photo(i, "김민지", LocalDateTime.of(2026, 11, 1, 0, 0).plusMinutes(i)));
        }
        SharedMediaBrowser.Page page = SharedMediaBrowser.browse(many, null, null, null, null, 1000);
        assertThat(page.items()).hasSize(SharedMediaBrowser.MAX_SIZE);
        assertThat(page.hasMore()).isTrue();
    }
}
