package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ChatMediaType;
import com.silverithm.vehicleplacementsystem.entity.ChatMessage;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 채팅방 '주고받은 파일'을 종류·검색어·달로 거르고 한 쪽씩 잘라 준다.
 *
 * <p><b>왜 필요했나.</b> 가장 큰 방은 사진 4,922장·파일 453개(2026-11-24)인데 예전 API는 이걸
 * 한 번에 통째로 내려보냈고, 앱·웹은 그대로 한 줄씩 5천 줄을 그렸다. 검색도, 날짜로 찾기도,
 * 사진만 보기도 안 됐다(사용자 "검색도 안 되고 날짜별로도 안 되고 한 줄로만 쭉 나온다").
 *
 * <p><b>왜 SQL이 아니라 여기서 거르나.</b> 사진·동영상·파일 구분은 {@link ChatMediaType#resolve}가
 * MIME 종류와 확장자를 함께 보고 정한다(옛 동영상은 FILE로 저장돼 있다). 이 규칙을 SQL에 옮겨 적으면
 * 두 곳이 어긋난다. 방의 첨부 행을 읽는 비용은 예전 API와 같고, 응답에 싣는 건 한 쪽뿐이라
 * 전송량·화면 그리기가 크게 준다.
 *
 * <p>입력은 순서와 상관없이 받아 최신순(만든 시각, 같으면 id 큰 쪽)으로 다시 세운다.
 */
public final class SharedMediaBrowser {

    /** 한 쪽 최대 개수 — 큰 값을 보내도 이만큼만 자른다 */
    public static final int MAX_SIZE = 200;
    public static final int DEFAULT_SIZE = 60;

    private SharedMediaBrowser() {
    }

    /** 달별 개수 — 화면의 '달로 이동' 목록에 쓴다. month는 "yyyy-MM" */
    public record MonthCount(String month, long count) {
    }

    /**
     * @param items   이번 쪽(최신순)
     * @param hasMore 더 오래된 항목이 남았는지
     * @param counts  검색어에 맞는 것 중 종류별 개수(IMAGE·VIDEO·FILE) — 탭 숫자
     * @param months  검색어·종류에 맞는 것의 달별 개수(최신 달 먼저)
     */
    public record Page(List<ChatMessage> items, boolean hasMore, Map<String, Long> counts,
                       List<MonthCount> months) {
    }

    private record Classified(ChatMessage message, String kind) {
    }

    /**
     * @param all      방의 첨부 메시지(지운 것 제외) — 순서 무관
     * @param kind     IMAGE·VIDEO·FILE 중 하나(대소문자 무관, PHOTO는 IMAGE로). 비었거나 ALL이면 모두
     * @param query    파일 이름·보낸 사람 이름에 들어 있는 글자(대소문자 무관). 비었으면 거르지 않는다
     * @param beforeId 이 id 다음(더 오래된 쪽)부터. 없으면 처음부터
     * @param month    "yyyy-MM" — 이 달의 마지막 항목부터 시작(달로 이동). beforeId가 있으면 무시
     * @param size     한 쪽 개수(1~{@link #MAX_SIZE})
     */
    public static Page browse(List<ChatMessage> all, String kind, String query, Long beforeId,
                              String month, int size) {
        String wantedKind = normalizeKind(kind);
        String needle = normalizeQuery(query);
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));

        List<Classified> matched = new ArrayList<>();
        for (ChatMessage m : sortedNewestFirst(all)) {
            String k = ChatMediaType.resolve(m.getType() == null ? null : m.getType().name(),
                    m.getMimeType(), m.getFileName());
            if (k == null) {
                continue;   // 첨부가 아닌 메시지는 셈에서 뺀다
            }
            if (needle != null && !matches(m, needle)) {
                continue;
            }
            matched.add(new Classified(m, k));
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(ChatMediaType.IMAGE, 0L);
        counts.put(ChatMediaType.VIDEO, 0L);
        counts.put(ChatMediaType.FILE, 0L);
        for (Classified c : matched) {
            counts.merge(c.kind(), 1L, Long::sum);
        }

        List<ChatMessage> ofKind = new ArrayList<>();
        for (Classified c : matched) {
            if (wantedKind == null || wantedKind.equals(c.kind())) {
                ofKind.add(c.message());
            }
        }

        List<MonthCount> months = monthCounts(ofKind);
        int start = startIndex(ofKind, beforeId, month);
        int end = Math.min(start + pageSize, ofKind.size());
        List<ChatMessage> items = start >= ofKind.size() ? List.of() : List.copyOf(ofKind.subList(start, end));
        return new Page(items, end < ofKind.size(), counts, months);
    }

    static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String k = kind.trim().toUpperCase(Locale.ROOT);
        return switch (k) {
            case "IMAGE", "PHOTO" -> ChatMediaType.IMAGE;
            case "VIDEO" -> ChatMediaType.VIDEO;
            case "FILE" -> ChatMediaType.FILE;
            default -> null;   // ALL 등 모르는 값은 모두 보기
        };
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() ? null : q;
    }

    private static boolean matches(ChatMessage m, String needle) {
        return contains(m.getFileName(), needle) || contains(m.getSenderName(), needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static List<ChatMessage> sortedNewestFirst(List<ChatMessage> all) {
        List<ChatMessage> copy = new ArrayList<>(all);
        copy.sort(Comparator
                .comparing(ChatMessage::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ChatMessage::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return copy;
    }

    private static List<MonthCount> monthCounts(List<ChatMessage> newestFirst) {
        Map<String, Long> byMonth = new LinkedHashMap<>();
        for (ChatMessage m : newestFirst) {
            if (m.getCreatedAt() == null) {
                continue;
            }
            byMonth.merge(YearMonth.from(m.getCreatedAt()).toString(), 1L, Long::sum);
        }
        List<MonthCount> out = new ArrayList<>();
        byMonth.forEach((k, v) -> out.add(new MonthCount(k, v)));
        return out;
    }

    private static int startIndex(List<ChatMessage> newestFirst, Long beforeId, String month) {
        if (beforeId != null) {
            for (int i = 0; i < newestFirst.size(); i++) {
                if (beforeId.equals(newestFirst.get(i).getId())) {
                    return i + 1;
                }
            }
            // 그 사이 지워져 목록에 없으면 id로 이어 간다(id는 만든 순서로 커진다)
            for (int i = 0; i < newestFirst.size(); i++) {
                Long id = newestFirst.get(i).getId();
                if (id != null && id < beforeId) {
                    return i;
                }
            }
            return newestFirst.size();
        }
        YearMonth ym = parseMonth(month);
        if (ym != null) {
            LocalDateTime nextMonthStart = ym.plusMonths(1).atDay(1).atStartOfDay();
            for (int i = 0; i < newestFirst.size(); i++) {
                LocalDateTime at = newestFirst.get(i).getCreatedAt();
                if (at != null && at.isBefore(nextMonthStart)) {
                    return i;
                }
            }
            return newestFirst.size();
        }
        return 0;
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            return null;   // 모르는 형식은 처음부터
        }
    }
}
