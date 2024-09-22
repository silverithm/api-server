package com.silverithm.vehicleplacementsystem.entity;

public enum DurationScore {


    ZERO(0, 10000),               // 0m인 경우 압도적으로 높은 점수 부여
    VERY_SHORT(100, 950),    // 100m 이하
    EXTRA_SHORT(200, 900),   // 200m 이하
    SHORT(300, 850),         // 300m 이하
    SHORT_MEDIUM(400, 800),  // 400m 이하
    MEDIUM(500, 750),        // 500m 이하
    MEDIUM_LONG(600, 700),   // 600m 이하
    LONG(700, 650),          // 700m 이하
    LONGER(800, 600),        // 800m 이하
    VERY_LONG(900, 550),     // 900m 이하
    EXTRA_LONG(1000, 500),   // 1000m 이하
    ULTRA_LONG(1100, 450),   // 1100m 이하
    FAR(1200, 400),          // 1200m 이하
    FURTHER(1300, 350),      // 1300m 이하
    DISTANT(1400, 300),      // 1400m 이하
    VERY_DISTANT(1500, 250), // 1500m 이하
    EXTRA_DISTANT(1600, 200),// 1600m 이하
    FAR_AWAY(1700, 150),     // 1700m 이하
    FARTHER(1800, 100),      // 1800m 이하
    FARTHEST(1900, 50),      // 1900m 이하
    OUT_OF_RANGE(Integer.MAX_VALUE, 0);       // 1900m 초과

    private final int maxDuration;
    private final double score;

    DurationScore(int maxDistance, double score) {
        this.maxDuration = maxDistance;
        this.score = score;
    }

    public static double getScore(double distance) {
        for (DurationScore ds : DurationScore.values()) {
            if (distance <= ds.maxDuration) {
                return ds.score;
            }
        }
        return 0; // Fallback if no range matches
    }
}