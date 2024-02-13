package com.silverithm.vehicleplacementsystem.controller;


import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import com.silverithm.vehicleplacementsystem.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;

    @PostMapping("/api/v1/employee")
    public String elderAdd(@RequestBody AddEmployeeRequest addEmployeeRequest) {
        employeeService.addEmployee(addEmployeeRequest);
        return "Success";
    }
}
