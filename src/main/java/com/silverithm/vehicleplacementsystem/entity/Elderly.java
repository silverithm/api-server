package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Getter
@NoArgsConstructor
public class Elderly extends Node {


    private String name;

    private String homeAddressName;
    @Embedded
    private Location homeAddress;
    private boolean requiredFrontSeat;


    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    public Elderly(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat, AppUser user) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.user = user;
    }

    public Elderly(String name, boolean requiredFrontSeat, Company company) {
        this.name = name;
        this.requiredFrontSeat = requiredFrontSeat;
        this.company = company;
    }

    public Elderly(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat, Company company) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.company = company;
    }

    public void update(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void update(boolean requiredFrontSeat) {
        this.requiredFrontSeat = requiredFrontSeat;
    }
}