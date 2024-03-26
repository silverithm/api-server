package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@NoArgsConstructor
public class Elderly extends Node {


    private String name;
    @Embedded
    private Location homeAddress;
    private boolean requiredFrontSeat;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    public Elderly(String name, Location homeAddress, boolean requiredFrontSeat, AppUser user) {
        this.name = name;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public Location getHomeAddress() {
        return homeAddress;
    }

    public boolean isRequiredFrontSeat() {
        return requiredFrontSeat;
    }

    public void update(String name, Location homeAddress, boolean requiredFrontSeat) {
        this.name = name;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
    }


}