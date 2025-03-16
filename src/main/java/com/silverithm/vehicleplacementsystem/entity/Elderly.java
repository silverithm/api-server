package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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

    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime modifiedAt;


    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    public Elderly(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat, AppUser user) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
        this.user = user;
    }


    public void update(String name, String homeAddressName, Location homeAddress, boolean requiredFrontSeat) {
        this.name = name;
        this.homeAddressName = homeAddressName;
        this.homeAddress = homeAddress;
        this.requiredFrontSeat = requiredFrontSeat;
    }

    public void update(boolean requiredFrontSeat) {
        this.requiredFrontSeat = requiredFrontSeat;
    }
}