package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class Company extends Node {

    @Embedded
    private Location companyAddress;

    public Company(Location companyAddress) {
        this.companyAddress = companyAddress;
    }
}
