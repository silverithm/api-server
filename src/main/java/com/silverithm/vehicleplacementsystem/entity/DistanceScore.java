package com.silverithm.vehicleplacementsystem.entity;

public enum DistanceScore {

    ZERO(0, 10000),               // 0m인 경우 압도적으로 높은 점수 부여
    VERY_SHORT(1000, 950),    // 100m 이하
    EXTRA_SHORT(1500, 900),   // 200m 이하
    SHORT(2000, 850),         // 300m 이하
    SHORT_MEDIUM(2500, 800),  // 400m 이하
    MEDIUM(3000, 750),        // 500m 이하
    MEDIUM_LONG(3500, 700),   // 600m 이하
    LONG(4000, 650),          // 700m 이하
    LONGER(4500, 600),        // 800m 이하
    VERY_LONG(5000, 550),     // 900m 이하
    EXTRA_LONG(5500, 500),   // 1000m 이하
    ULTRA_LONG(6000, 450),   // 1100m 이하
    FAR(6500, 400),          // 1200m 이하
    FURTHER(7000, 350),      // 1300m 이하
    DISTANT(7500, 300),      // 1400m 이하
    VERY_DISTANT(8000, 250), // 1500m 이하
    EXTRA_DISTANT(8500, 200),// 1600m 이하
    FAR_AWAY(9000, 150),     // 1700m 이하
    FARTHER(9500, 100),      // 1800m 이하
    FARTHEST(10000, 50),      // 1900m 이하
    OUT_OF_RANGE(Integer.MAX_VALUE, 0);       // 1900m 초과

    private final int maxDistance;
    private final double score;

    DistanceScore(int maxDistance, double score) {
        this.maxDistance = maxDistance;
        this.score = score;
    }

    public static double getScore(double distance) {
        for (DistanceScore ds : DistanceScore.values()) {
            if (distance <= ds.maxDistance) {
                return ds.score;
            }
        }
        return 0; // Fallback if no range matches
    }
}