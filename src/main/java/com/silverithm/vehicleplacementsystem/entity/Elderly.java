package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class Elderly extends Person {

    @Embedded
    private Location homeAddress;
    private boolean requiredFrontSeat;

    public Elderly(Location homeAddress, boolean requiredFrontSeat) {
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