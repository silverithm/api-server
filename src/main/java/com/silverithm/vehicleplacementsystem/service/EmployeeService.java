package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    public void addEmployee(AddEmployeeRequest addEmployeeRequest) {
        Employee employee = new Employee(addEmployeeRequest.name(), addEmployeeRequest.workPlace(),
                addEmployeeRequest.homeAddress(),
                addEmployeeRequest.maxCapacity());
        employeeRepository.save(employee);
    }
}
