package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@NoArgsConstructor
public class Elderly extends Node {


    private String name;
    private int age;
    @Embedded
    private Location homeAddress;
    private boolean requiredFrontSeat;

    public Elderly(String name, int age, Location homeAddress, boolean requiredFrontSeat) {
        this.name = name;
        this.age = age;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
    }


    public Location getHomeAddress() {
        return homeAddress;
    }

    public boolean isRequiredFrontSeat() {
        return requiredFrontSeat;
    }

}