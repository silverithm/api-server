package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 실험실 서비스들이 함께 쓰는 입력 검증·정리 도구. 오류는 모두 400 {"message"}로 나간다. */
final class LabValidation {

    private static final Pattern MONTH = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
    private static final Pattern YEAR = Pattern.compile("^\\d{4}$");
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter DATE_TIME_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final Pattern CLOCK = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private LabValidation() {
    }

    /** 서울 현재 시각 — 서버 시간대와 상관없이 서울 기준으로 "지금"을 판정한다 */
    static LocalDateTime seoulNow() {
        return LocalDateTime.now(SEOUL);
    }

    static LocalDate seoulToday() {
        return LocalDate.now(SEOUL);
    }

    /** 'YYYY-MM-DDTHH:MM'(초 없음, 서울 시각)만 통과. 비어 있으면 null */
    static LocalDateTime dateTimeOrNull(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        if (!DATE_TIME.matcher(text).matches()) {
            throw bad(label + "은(는) YYYY-MM-DDTHH:MM 형식이어야 합니다");
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(text);
            plausibleDate(parsed.toLocalDate(), label);
            return parsed;
        } catch (java.time.format.DateTimeParseException e) {
            throw bad(label + "은(는) YYYY-MM-DDTHH:MM 형식이어야 합니다");
        }
    }

    static String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_OUT);
    }

    /** 'HH:MM' 시각 — 필수. 분 단위 계산에 쓰므로 형식이 틀리면 400 */
    static String clock(String value, String label) {
        String text = value == null ? null : value.strip();
        if (text == null || text.isEmpty()) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        if (!CLOCK.matcher(text).matches()) {
            throw bad(label + "은(는) HH:MM 형식(예: 14:30)이어야 합니다");
        }
        return text;
    }

    /** 서울 오늘까지의 날짜 — 필수. 2000~2100년 밖이거나 오늘보다 미래면 400 (아직 일어나지 않은 일은 기록하지 않는다) */
    static LocalDate pastOrToday(LocalDate value, String label) {
        plausibleDate(value, label);
        if (value.isAfter(seoulToday())) {
            throw bad(label + "은(는) 오늘 이후일 수 없습니다. 아직 일어나지 않은 일은 기록하지 않습니다");
        }
        return value;
    }

    /** 비었으면 null, 있으면 {@link #pastOrToday} */
    static LocalDate pastOrTodayOrNull(LocalDate value, String label) {
        return value == null ? null : pastOrToday(value, label);
    }

    /** 'HH:mm' 시각 — 선택. 비었으면 null, 형식이 틀리면 400 */
    static java.time.LocalTime clockOrNull(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return java.time.LocalTime.parse(clock(value, label));
    }

    static String formatClock(java.time.LocalTime value) {
        return value == null ? null : value.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** 이 칸이 같은 종류의 id 목록에서 중복을 허용하지 않을 때 — 중복이 있으면 400, null 원소·개수 초과도 400 */
    static List<Long> distinctIds(List<Long> ids, int min, int max, String label) {
        if (ids == null || ids.size() < min) {
            throw bad(label + "은(는) 최소 " + min + "명 선택해 주세요");
        }
        if (ids.size() > max) {
            throw bad(label + "은(는) 최대 " + max + "명까지 선택할 수 있습니다");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw bad(label + " 목록에 올바르지 않은 값이 있습니다");
            }
            if (!unique.add(id)) {
                throw bad(label + " 목록에 같은 사람이 두 번 들어 있습니다");
            }
        }
        return List.copyOf(unique);
    }

    static CustomException bad(String message) {
        return new CustomException(message, HttpStatus.BAD_REQUEST);
    }

    /** 앞뒤 공백을 없애고, 비면 null. 길이를 넘으면 400 — 잘라서 저장하면 기록이 조용히 바뀐다. */
    static String text(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > max) {
            throw bad(label + "은(는) " + max + "자까지 쓸 수 있습니다");
        }
        return trimmed;
    }

    /** min~max 정수. 비었거나 범위 밖이면 400 — int/BIGINT 열 범위를 넘는 값이 저장 단계에서 500이 되지 않게 한다 */
    static long longInRange(Long value, long min, long max, String label) {
        if (value == null) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        if (value < min || value > max) {
            throw bad(label + "은(는) " + String.format("%,d", min) + "~" + String.format("%,d", max)
                    + " 사이여야 합니다");
        }
        return value;
    }

    static <T> T required(T value, String label) {
        if (value == null) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        return value;
    }

    static String requiredText(String value, int max, String label) {
        String text = text(value, max, label);
        if (text == null) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        return text;
    }

    /** 'YYYY-MM' 형식만 통과시킨다 (13월 같은 값도 거른다) */
    static String month(String value, String label) {
        if (value == null || !MONTH.matcher(value).matches()) {
            throw bad(label + "은(는) YYYY-MM 형식이어야 합니다");
        }
        return value;
    }

    /** 'YYYY' 4자리 연도. 쿼리 값이라 문자열로 받아 "20x6"이나 "99999" 같은 값을 400으로 거른다 */
    static int year(String value, String label) {
        if (value == null || !YEAR.matcher(value).matches()) {
            throw bad(label + "은(는) YYYY 형식이어야 합니다");
        }
        return year(Integer.parseInt(value), label);
    }

    /** 본문으로 받은 연도 — 2000~2100. SMALLINT 열 범위 안이고, 오타로 만든 먼 연도를 막는다 */
    static int year(Integer value, String label) {
        if (value == null) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        if (value < MIN_YEAR || value > MAX_YEAR) {
            throw bad(label + "은(는) " + MIN_YEAR + "~" + MAX_YEAR + " 사이여야 합니다");
        }
        return value;
    }

    /** 중복을 없애고 순서를 지킨 id 목록. null 원소·개수 초과는 400 — 입력이 null이면 빈 목록 */
    static List<Long> idList(List<Long> ids, int max, String label) {
        if (ids == null) {
            return List.of();
        }
        if (ids.size() > max) {
            throw bad(label + "은(는) 최대 " + max + "명까지 선택할 수 있습니다");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw bad(label + " 목록에 올바르지 않은 값이 있습니다");
            }
            unique.add(id);
        }
        return List.copyOf(unique);
    }

    static YearMonth yearMonth(String value, String label) {
        return YearMonth.parse(month(value, label));
    }

    static LocalDate date(String value, String label) {
        if (value == null || value.isBlank()) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw bad(label + "은(는) YYYY-MM-DD 형식이어야 합니다");
        }
    }

    /** 본문으로 받은 날짜 — 2000~2100년. DATE 열 범위 밖의 먼 날짜가 저장 단계에서 500이 되지 않게 한다 */
    static LocalDate plausibleDate(LocalDate value, String label) {
        if (value == null) {
            throw bad(label + "을(를) 입력해 주세요");
        }
        if (value.getYear() < MIN_YEAR || value.getYear() > MAX_YEAR) {
            throw bad(label + "은(는) " + MIN_YEAR + "~" + MAX_YEAR + "년 사이여야 합니다");
        }
        return value;
    }

    static String oneOf(String value, Set<String> allowed, String label) {
        String text = text(value, 40, label);
        if (text == null || !allowed.contains(text)) {
            throw bad(label + " 값이 올바르지 않습니다");
        }
        return text;
    }

    /**
     * 같은 키로 동시에 저장하면 둘 다 "없음"을 보고 INSERT해 한쪽이 유니크 키에 막힌다.
     * 그때 한 번만 다시 시도하면 두 번째는 이긴 쪽 행을 찾아 UPDATE로 합쳐진다.
     * 트랜잭션 안에서는 한 번 터진 세션을 쓸 수 없으므로 트랜잭션 전체를 새로 시작해 다시 건다.
     */
    static <T> T upsertWithRetry(TransactionTemplate tx, Supplier<T> body) {
        try {
            return tx.execute(status -> body.get());
        } catch (DataIntegrityViolationException e) {
            return tx.execute(status -> body.get());
        }
    }
}
