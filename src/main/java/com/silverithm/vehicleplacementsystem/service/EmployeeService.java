package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private ElderRepository elderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeocodingService geocodingService;

    public void addEmployee(Long userId, AddEmployeeRequest addEmployeeRequest) throws Exception {

        Location homeAddress = geocodingService.getAddressCoordinates(addEmployeeRequest.homeAddress());
        Location workPlace = geocodingService.getAddressCoordinates(addEmployeeRequest.workPlace());

        if (homeAddress == null || workPlace == null) {
            throw new Exception();
        }

        System.out.println(homeAddress + " " + addEmployeeRequest.homeAddress());
        System.out.println(workPlace + " " + addEmployeeRequest.workPlace());

        AppUser user = userRepository.findById(userId).orElseThrow();

        Employee employee = new Employee(addEmployeeRequest.workPlace(), addEmployeeRequest.homeAddress(),
                addEmployeeRequest.name(), workPlace, homeAddress,
                addEmployeeRequest.maxCapacity(), addEmployeeRequest.isDriver(), user);
        employeeRepository.save(employee);
    }

    public List<EmployeeDTO> getEmployees(Long userId) {

        List<Employee> employees = employeeRepository.findByUserId(userId);

        List<EmployeeDTO> employeeDTOS = employees.stream()
                .map(employee -> new EmployeeDTO(employee.getId(), employee.getName(), employee.getHomeAddressName(),
                        employee.getWorkPlaceAddressName(), employee.getHomeAddress(),
                        employee.getWorkPlace(),
                        employee.getMaximumCapacity(), employee.getIsDriver()))
                .sorted(Comparator.comparing(EmployeeDTO::name)).collect(Collectors.toList());

        return employeeDTOS;

    }


    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }





    @Transactional
    public void updateEmployee(Long id, EmployeeUpdateRequestDTO employeeUpdateRequestDTO) throws Exception {
        Location updatedHomeAddress = geocodingService.getAddressCoordinates(employeeUpdateRequestDTO.homeAddress());
        Location updatedWorkPlace = geocodingService.getAddressCoordinates(employeeUpdateRequestDTO.workPlace());

        Employee employee = employeeRepository.findById(id).orElseThrow();

        employee.update(employeeUpdateRequestDTO.homeAddress(), employeeUpdateRequestDTO.workPlace(),
                employeeUpdateRequestDTO.name(), updatedHomeAddress,
                updatedWorkPlace, employeeUpdateRequestDTO.maxCapacity(), employeeUpdateRequestDTO.isDriver());
    }

}
