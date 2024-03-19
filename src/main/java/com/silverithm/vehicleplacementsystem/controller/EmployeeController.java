package com.silverithm.vehicleplacementsystem.controller;


import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import com.silverithm.vehicleplacementsystem.service.EmployeeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/api/v1/employees")
    public List<EmployeeDTO> getEmployees() {
        return employeeService.getEmployees();
    }

    @DeleteMapping("/api/v1/employee/{id}")
    public String getElders(@PathVariable("id") final Long id) {
        employeeService.deleteEmployee(id);
        return "Success";
    }

    @PutMapping("/api/v1/employee/{id}")
    public String updateElder(@PathVariable("id") final Long id,
                              @RequestBody ElderUpdateRequestDTO elderUpdateRequestDTO) {
        employeeService.updateEmployee(id, elderUpdateRequestDTO);
        return "Success";
    }
}
