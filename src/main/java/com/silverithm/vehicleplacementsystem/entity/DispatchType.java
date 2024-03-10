package com.silverithm.vehicleplacementsystem.entity;

public enum DispatchType {
    IN(0),
    OUT(1);
    private final int value;

    DispatchType(int value) {
        this.value = value;
    }
}
