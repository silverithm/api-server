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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue
    int id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column=@Column(name="workplace_latitude")),
            @AttributeOverride(name="longitude", column=@Column(name="workplace_longitude"))
    })
    private Location workplace;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column=@Column(name="home_latitude")),
            @AttributeOverride(name="longitude", column=@Column(name="home_longitude"))
    })
    private Location homeAddress;
    private int maximumCapacity;
    private String departureTime;
    private String visitOrder;

    public Employee(Location workplace, Location homeAddress, int maximumCapacity) {
        this.workplace = workplace;
        this.homeAddress = homeAddress;
        this.maximumCapacity = maximumCapacity;
    }


    public int getId() {
        return id;
    }

    public Location getWorkplace() {
        return workplace;
    }

    public Location getHomeAddress() {
        return homeAddress;
    }

    public int getMaximumCapacity() {
        return maximumCapacity;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getVisitOrder() {
        return visitOrder;
    }

    public void setVisitOrder(String visitOrder) {
        this.visitOrder = visitOrder;
    }

}

