package com.silverithm.vehicleplacementsystem.controller;


import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import com.silverithm.vehicleplacementsystem.service.EmployeeService;
import com.silverithm.vehicleplacementsystem.service.ExcelService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService employeeService;

    private final ExcelService excelService;

    @PostMapping("/api/v1/employee/{userId}")
    public String elderAdd(@PathVariable("userId") final Long userId,
                           @RequestBody AddEmployeeRequest addEmployeeRequest) throws Exception {
        employeeService.addEmployee(userId, addEmployeeRequest);
        return "Success";
    }

    @GetMapping("/api/v1/employees/{userId}")
    public List<EmployeeDTO> getEmployees(@PathVariable("userId") final Long userId) {
        return employeeService.getEmployees(userId);
    }

    @DeleteMapping("/api/v1/employee/{id}")
    public String getElders(@PathVariable("id") final Long id) {
        employeeService.deleteEmployee(id);
        return "Success";
    }

    @PutMapping("/api/v1/employee/{id}")
    public String updateElder(@PathVariable("id") final Long id,
                              @RequestBody EmployeeUpdateRequestDTO employeeUpdateRequestDTO) throws Exception {
        employeeService.updateEmployee(id, employeeUpdateRequestDTO);
        return "Success";
    }




}
