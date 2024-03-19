package com.silverithm.vehicleplacementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VehiclePlacementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(VehiclePlacementSystemApplication.class, args);
    }

}
