package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private GeocodingService geocodingService;

    public void addEmployee(AddEmployeeRequest addEmployeeRequest) {

        Location homeAddress = geocodingService.getAddressCoordinates(addEmployeeRequest.homeAddress());
        Location workPlace = geocodingService.getAddressCoordinates(addEmployeeRequest.workPlace());



        System.out.println(homeAddress + " " + addEmployeeRequest.homeAddress());
        System.out.println(workPlace + " " + addEmployeeRequest.workPlace());

        Employee employee = new Employee(addEmployeeRequest.name(), workPlace, homeAddress,
                addEmployeeRequest.maxCapacity());
        employeeRepository.save(employee);
    }
}
