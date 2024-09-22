package com.silverithm.vehicleplacementsystem.entity;

public enum DispatchType {
    DISTANCE_IN(0),
    DISTANCE_OUT(1),

    DURATION_IN(2),
    DURATION_OUT(3);


    private final int value;

    DispatchType(int value) {
        this.value = value;
    }
}
