package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Controller;

@Entity
@NoArgsConstructor
public class Elder {

    @Id
    @GeneratedValue
    long id;
    String name;
    int age;
    String address;

    public Elder(AddElderRequest addElderRequest) {
        this.name = addElderRequest.name();
        this.age = addElderRequest.age();
        this.address = addElderRequest.address();
    }

}
