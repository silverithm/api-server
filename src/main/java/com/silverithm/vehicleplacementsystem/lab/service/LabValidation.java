package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import java.time.LocalDate;
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

    private LabValidation() {
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
