package com.silverithm.vehicleplacementsystem.lab.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 욕창(Braden)·낙상(Morse)·인지기능 위험도 점수 계산 — 서버가 기준이다(웹은 미리보기용으로 같은 규칙을 따라 한다).
 *
 * <p>화면이 보낸 합계나 단계를 믿지 않는다. 항목 점수만 받아 여기서 다시 계산해 저장하므로, 웹과
 * 서버 규칙이 어긋나도 저장되는 값은 항상 이 표를 따른다. 항목이 빠지거나 모르는 항목이 있거나
 * 허용되지 않는 값이면 400이다.
 *
 * <p>인지기능(COGNITIVE)은 합산 척도가 아니라 검사 도구(KMMSE·CIST·외부기관·치매진단)와 판정이라 점수표가 다르다.
 * 도구 점수는 그대로 total에, 직원이 고른 판정은 riskLevel에 담는다. 판정을 점수에서 서버가 추측하지 않는 이유는
 * 같은 점수라도 연령·학력 보정을 임상 판단으로 하기 때문이다(웹은 KMMSE 제안값만 보여 준다).
 */
final class LabRiskScoring {

    static final String BRADEN = "BRADEN";
    static final String MORSE = "MORSE";
    static final String COGNITIVE = "COGNITIVE";

    private static final Set<String> COGNITIVE_TOOLS = Set.of("KMMSE", "CIST", "EXTERNAL", "DIAGNOSIS");
    private static final Set<String> COGNITIVE_TOOLS_WITH_SCORE = Set.of("KMMSE", "CIST");
    private static final Set<String> COGNITIVE_JUDGMENTS = Set.of("NORMAL", "SUSPECTED", "IMPAIRED");
    private static final Set<String> COGNITIVE_KEYS = Set.of("tool", "score", "judgment", "institution");
    private static final int COGNITIVE_MAX_SCORE = 30;
    private static final int INSTITUTION_MAX = 50;

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

    record Result(Map<String, Object> scores, int total, String riskLevel) {
    }

    static boolean isKind(String kind) {
        return BRADEN.equals(kind) || MORSE.equals(kind) || COGNITIVE.equals(kind);
    }

    /** 점수를 검증하고 합계·단계를 계산한다. 항목은 정의된 순서로 정렬해 돌려준다. */
    static Result score(String kind, Map<String, ?> input) {
        Map<String, Set<Integer>> items;
        if (BRADEN.equals(kind)) {
            items = BRADEN_ITEMS;
        } else if (MORSE.equals(kind)) {
            items = MORSE_ITEMS;
        } else if (COGNITIVE.equals(kind)) {
            return scoreCognitive(input);
        } else {
            throw LabValidation.bad("평가 종류는 BRADEN, MORSE, COGNITIVE 중 하나여야 합니다");
        }
        if (input == null) {
            throw LabValidation.bad("항목 점수를 입력해 주세요");
        }
        for (String key : input.keySet()) {
            if (!items.containsKey(key)) {
                throw LabValidation.bad("알 수 없는 평가 항목입니다: " + key);
            }
        }

        Map<String, Object> scores = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, Set<Integer>> item : items.entrySet()) {
            Object raw = input.get(item.getKey());
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

    /**
     * 인지기능 평가 — {tool, score?, judgment, institution?}.
     * KMMSE·CIST는 0~30점이 필수, 외부기관 결과(EXTERNAL)는 기관명이 필수(어디서 받은 결과인지 근거가 남아야 한다).
     * total은 점수(없으면 0), riskLevel은 판정이다.
     */
    private static Result scoreCognitive(Map<String, ?> input) {
        if (input == null) {
            throw LabValidation.bad("인지기능 평가 내용을 입력해 주세요");
        }
        for (String key : input.keySet()) {
            if (!COGNITIVE_KEYS.contains(key)) {
                throw LabValidation.bad("알 수 없는 평가 항목입니다: " + key);
            }
        }
        String tool = cognitiveText(input.get("tool"), "검사 도구");
        if (tool == null || !COGNITIVE_TOOLS.contains(tool)) {
            throw LabValidation.bad("검사 도구는 KMMSE, CIST, EXTERNAL, DIAGNOSIS 중 하나여야 합니다");
        }
        String judgment = cognitiveText(input.get("judgment"), "판정");
        if (judgment == null || !COGNITIVE_JUDGMENTS.contains(judgment)) {
            throw LabValidation.bad("판정은 NORMAL(정상), SUSPECTED(의심), IMPAIRED(저하) 중 하나여야 합니다");
        }

        Object rawScore = input.get("score");
        Integer score = null;
        if (rawScore != null) {
            score = cognitiveScore(rawScore);
        } else if (COGNITIVE_TOOLS_WITH_SCORE.contains(tool)) {
            throw LabValidation.bad(tool + " 검사는 점수(0~30)를 입력해야 합니다");
        }

        String institution = cognitiveText(input.get("institution"), "기관명");
        if (institution != null && institution.length() > INSTITUTION_MAX) {
            throw LabValidation.bad("기관명은 " + INSTITUTION_MAX + "자까지 쓸 수 있습니다");
        }
        if ("EXTERNAL".equals(tool) && institution == null) {
            throw LabValidation.bad("외부기관 결과는 검사한 기관명을 입력해야 합니다");
        }

        // 저장 순서는 고정한다 — 같은 평가는 항상 같은 JSON이 되어 비교·점검이 쉽다
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("tool", tool);
        if (score != null) {
            scores.put("score", score);
        }
        scores.put("judgment", judgment);
        if (institution != null) {
            scores.put("institution", institution);
        }
        return new Result(scores, score == null ? 0 : score, judgment);
    }

    /** 문자열 항목 — 문자열이 아니거나 비면 null(비어 있음과 같게 본다). 앞뒤 공백은 없애고 도구·판정은 대문자로 맞춘다 */
    private static String cognitiveText(Object raw, String label) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String text)) {
            throw LabValidation.bad(label + "은(는) 글자로 입력해야 합니다");
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return "기관명".equals(label) ? trimmed : trimmed.toUpperCase();
    }

    private static int cognitiveScore(Object raw) {
        if (!(raw instanceof Number number)) {
            throw LabValidation.bad("점수는 0~30 사이의 정수여야 합니다");
        }
        int value = wholeNumber(number, "score");
        if (value < 0 || value > COGNITIVE_MAX_SCORE) {
            throw LabValidation.bad("점수는 0~30 사이여야 합니다 (입력값 " + value + ")");
        }
        return value;
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
    private static int wholeNumber(Object raw, String key) {
        if (!(raw instanceof Number number)) {
            throw LabValidation.bad("점수는 정수여야 합니다: " + key);
        }
        double d = number.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || Math.abs(d) > 1_000_000) {
            throw LabValidation.bad("점수는 정수여야 합니다: " + key);
        }
        return (int) d;
    }

    private static List<Integer> sorted(Set<Integer> values) {
        return values.stream().sorted().toList();
    }
}
