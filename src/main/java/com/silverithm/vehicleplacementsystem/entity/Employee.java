package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.xml.sax.helpers.LocatorImpl;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Employee extends Node {


    private String homeAddressName;
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "home_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "home_longitude"))
    })
    private Location homeAddress;
    private int maximumCapacity;

    private Boolean isDriver;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    public Employee(String homeAddressName, String name, Company company, Location homeAddress, int maximumCapacity,
                    Boolean isDriver, AppUser user) {

        this.homeAddressName = homeAddressName;
        this.name = name;
        this.company = company;
        this.homeAddress = homeAddress;
        this.maximumCapacity = maximumCapacity;
        this.isDriver = isDriver;
        this.user = user;
    }


    public void update(String homeAddressName, String workPlaceAddressName, String name, Location homeAddress,
                       Location workPlace, int maxCapacity, Boolean isDriver) {
        this.homeAddressName = homeAddressName;
        this.name = name;
        this.homeAddress = homeAddress;
        this.maximumCapacity = maxCapacity;
        this.isDriver = isDriver;
        updateWorkPlace(workPlaceAddressName, workPlace);
    }

    public void updateWorkPlace(String workPlaceAddressName, Location workPlace) {
        company.updateAddress(workPlaceAddressName, workPlace);
    }

}

