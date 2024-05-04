package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.xml.sax.helpers.LocatorImpl;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Employee extends Node {


    private String workPlaceAddressName;
    private String homeAddressName;

    private String name;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "workplace_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "workplace_longitude"))
    })

    private Location workPlace;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "home_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "home_longitude"))
    })
    private Location homeAddress;
    private int maximumCapacity;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    public Employee(String name, Location workplace, Location homeAddress, int maximumCapacity, AppUser user) {
        this.name = name;
        this.workPlace = workplace;
        this.homeAddress = homeAddress;
        this.maximumCapacity = maximumCapacity;
        this.user = user;
    }


    public void update(String homeAddressName, String workPlaceAddressName, String name, Location homeAddress,
                       Location workPlace, int maxCapacity) {
        this.homeAddressName = homeAddressName;
        this.workPlaceAddressName = workPlaceAddressName;
        this.name = name;
        this.homeAddress = homeAddress;
        this.workPlace = workPlace;
        this.maximumCapacity = maxCapacity;
    }
}

