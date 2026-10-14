package com.silverithm.vehicleplacementsystem.lab.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 욕창(Braden)·낙상(Morse) 위험도 점수 계산 — 서버가 기준이다(웹은 미리보기용으로 같은 규칙을 따라 한다).
 *
 * <p>화면이 보낸 합계나 단계를 믿지 않는다. 항목 점수만 받아 여기서 다시 계산해 저장하므로, 웹과
 * 서버 규칙이 어긋나도 저장되는 값은 항상 이 표를 따른다. 항목이 빠지거나 모르는 항목이 있거나
 * 허용되지 않는 값이면 400이다.
 */
final class LabRiskScoring {

    static final String BRADEN = "BRADEN";
    static final String MORSE = "MORSE";

    /** 항목 순서가 곧 저장·응답 순서다 */
    private static final Map<String, Set<Integer>> BRADEN_ITEMS = new LinkedHashMap<>();
    private static final Map<String, Set<Integer>> MORSE_ITEMS = new LinkedHashMap<>();

    static {
        BRADEN_ITEMS.put("sensory", Set.of(1, 2, 3, 4));
        BRADEN_ITEMS.put("moisture", Set.of(1, 2, 3, 4));
        BRADEN_ITEMS.put("activity", Set.of(1, 2, 3, 4));
        BRADEN_ITEMS.put("mobility", Set.of(1, 2, 3, 4));
        BRADEN_ITEMS.put("nutrition", Set.of(1, 2, 3, 4));
        BRADEN_ITEMS.put("friction", Set.of(1, 2, 3));

        MORSE_ITEMS.put("history", Set.of(0, 25));
        MORSE_ITEMS.put("secondaryDiagnosis", Set.of(0, 15));
        MORSE_ITEMS.put("ambulatoryAid", Set.of(0, 15, 30));
        MORSE_ITEMS.put("ivTherapy", Set.of(0, 20));
        MORSE_ITEMS.put("gait", Set.of(0, 10, 20));
        MORSE_ITEMS.put("mentalStatus", Set.of(0, 15));
    }

    private LabRiskScoring() {
    }

    record Result(Map<String, Integer> scores, int total, String riskLevel) {
    }

    static boolean isKind(String kind) {
        return BRADEN.equals(kind) || MORSE.equals(kind);
    }

    /** 점수를 검증하고 합계·단계를 계산한다. 항목은 정의된 순서로 정렬해 돌려준다. */
    static Result score(String kind, Map<String, Number> input) {
        Map<String, Set<Integer>> items;
        if (BRADEN.equals(kind)) {
            items = BRADEN_ITEMS;
        } else if (MORSE.equals(kind)) {
            items = MORSE_ITEMS;
        } else {
            throw LabValidation.bad("평가 종류는 BRADEN 또는 MORSE여야 합니다");
        }
        if (input == null) {
            throw LabValidation.bad("항목 점수를 입력해 주세요");
        }
        for (String key : input.keySet()) {
            if (!items.containsKey(key)) {
                throw LabValidation.bad("알 수 없는 평가 항목입니다: " + key);
            }
        }

        Map<String, Integer> scores = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Set<Integer>> item : items.entrySet()) {
            Number raw = input.get(item.getKey());
            if (raw == null) {
                throw LabValidation.bad("평가 항목이 빠졌습니다: " + item.getKey());
            }
            int value = wholeNumber(raw, item.getKey());
            if (!item.getValue().contains(value)) {
                throw LabValidation.bad("점수 범위를 벗어났습니다: " + item.getKey() + "=" + value
                        + " (가능한 값 " + sorted(item.getValue()) + ")");
            }
            scores.put(item.getKey(), value);
            total += value;
        }
        return new Result(scores, total, BRADEN.equals(kind) ? bradenLevel(total) : morseLevel(total));
    }

    /** 6~23점. 낮을수록 위험하다 */
    static String bradenLevel(int total) {
        if (total <= 9) {
            return "VERY_HIGH";
        }
        if (total <= 12) {
            return "HIGH";
        }
        if (total <= 14) {
            return "MODERATE";
        }
        if (total <= 18) {
            return "MILD";
        }
        return "NONE";
    }

    /** 0~125점. 높을수록 위험하다 */
    static String morseLevel(int total) {
        if (total <= 24) {
            return "LOW";
        }
        return total <= 44 ? "MODERATE" : "HIGH";
    }

    /** 3.5나 1e20 같은 값이 정수로 잘려 통과하지 않게 정확히 정수인 경우만 받는다 */
    private static int wholeNumber(Number raw, String key) {
        double d = raw.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || Math.abs(d) > 1_000_000) {
            throw LabValidation.bad("점수는 정수여야 합니다: " + key);
        }
        return (int) d;
    }

    private static List<Integer> sorted(Set<Integer> values) {
        return values.stream().sorted().toList();
    }
}
