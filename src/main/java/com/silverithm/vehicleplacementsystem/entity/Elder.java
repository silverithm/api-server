package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.springframework.stereotype.Controller;

@Entity
public class Elder {

    @Id
    @GeneratedValue
    long id;
    String name;
    int age;
    String address;

}
