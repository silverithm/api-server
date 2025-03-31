package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String addressName;

    @Embedded
    private Location companyAddress;


    public Company(String name, String addressName, Location companyAddress) {
        this.name = name;
        this.addressName = addressName;
        this.companyAddress = companyAddress;
    }

    public static Company of(String companyName, String addressName, Location companyLocation) {
        return new Company(companyName, addressName, companyLocation);
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateAddress(String addressName, Location companyAddress) {
        this.addressName = addressName;
        this.companyAddress = companyAddress;
    }
}
