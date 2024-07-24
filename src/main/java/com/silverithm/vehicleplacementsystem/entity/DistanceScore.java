package com.silverithm.vehicleplacementsystem.entity;

public enum DistanceScore {
    ZERO(0, 100),
    EXTRA_SHORT(100, 8),
    SHORT(250, 5),
    MEDIUM(500, 1.5),
    LONG(750, 1.0),
    EXTRA_LONG(1000, 0.5),
    OUT_OF_RANGE(Integer.MAX_VALUE, 0);

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