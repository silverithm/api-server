package com.silverithm.vehicleplacementsystem;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableBatchProcessing
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
public class VehiclePlacementSystemApplication {
    public static void main(String[] args) throws Exception {
        SpringApplication.run(VehiclePlacementSystemApplication.class, args);
    }
}
