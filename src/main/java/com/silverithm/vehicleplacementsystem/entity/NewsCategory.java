package com.silverithm.vehicleplacementsystem.entity;

/**
 * 요양 소식 카테고리. key는 프론트엔드(케어브이 광장)와 공유하는 소문자 식별자.
 */
public enum NewsCategory {
    ABUSE("abuse"),      // 학대·안전
    POLICY("policy"),    // 제도·수가
    EVAL("eval"),        // 평가
    FIELD("field");      // 현장소식

    private final String key;

    NewsCategory(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static NewsCategory fromKey(String key) {
        for (NewsCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown news category: " + key);
    }
}
