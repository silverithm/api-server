package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import com.silverithm.vehicleplacementsystem.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VehiclePlacementSystemApplication {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private EmployeeService employeeService;

    public static void main(String[] args) {
        SpringApplication.run(VehiclePlacementSystemApplication.class, args);
    }

}
