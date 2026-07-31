package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.CreateCompanyDto;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import jakarta.transaction.TransactionScoped;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
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
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyCodeService companyCodeService;

    @Autowired
    private ResourceScopeGuard resourceScopeGuard;

    public void addEmployee(Long userId, AddEmployeeRequest addEmployeeRequest) throws Exception {

        Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        Location workPlace = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)

        AppUser user = userRepository.findById(userId).orElseThrow();

        Employee employee = new Employee(addEmployeeRequest.homeAddress(),
                addEmployeeRequest.name(), user.getCompany(), homeAddress,
                addEmployeeRequest.maxCapacity(), addEmployeeRequest.isDriver(), user);
        employeeRepository.save(employee);
    }

    public List<EmployeeDTO> getEmployees(Long userId) {

        List<Employee> employees = employeeRepository.findByUserId(userId);

        List<EmployeeDTO> employeeDTOS = employees.stream()
                .map(employee -> new EmployeeDTO(employee.getId(), employee.getName(),
                        employee.getHomeAddressName(), employee.getCompany().getAddressName(),
                        employee.getHomeAddress(),
                        employee.getCompany().getCompanyAddress(),
                        employee.getMaximumCapacity(), employee.getIsDriver()))
                .sorted(Comparator.comparing(EmployeeDTO::name)).collect(Collectors.toList());

        return employeeDTOS;

    }


    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }


    @Transactional
    public void updateEmployee(Long id, EmployeeUpdateRequestDTO employeeUpdateRequestDTO) throws Exception {
        Location updatedHomeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        Location updatedWorkPlace = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)

        Employee employee = employeeRepository.findById(id).orElseThrow();
        resourceScopeGuard.requireSameCompany(employee.getCompany(),
                employee.getUser() != null ? employee.getUser().getCompany() : null);

        employee.update(employeeUpdateRequestDTO.homeAddress(), employeeUpdateRequestDTO.workPlace(),
                employeeUpdateRequestDTO.name(), updatedHomeAddress,
                updatedWorkPlace, employeeUpdateRequestDTO.maxCapacity(), employeeUpdateRequestDTO.isDriver());
    }


    @Transactional
    public void createEmployeeCompany(Long userId, CreateCompanyDto createCompanyDto) throws Exception {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        Location location = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        Company company = Company.of(createCompanyDto.companyName(), createCompanyDto.companyAddressName(),
                location);
        company.updateCompanyCode(companyCodeService.generateUniqueCode());
        companyRepository.save(company);

        List<Employee> employees = employeeRepository.findByUserId(user.getId());

        if (employees != null && !employees.isEmpty()) {
            for (Employee employee : employees) {
                employee.setCompany(company);
            }
        }

        user.addCompany(company);
    }

    @Transactional
    public void bulkAddEmployees(UserDetails userDetails, List<AddEmployeeRequest> employeeRequests) throws Exception {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        for (AddEmployeeRequest employeeRequest : employeeRequests) {
            Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
            Location workPlace = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)

            Employee employee = new Employee(employeeRequest.homeAddress(), employeeRequest.name(),
                    user.getCompany(), homeAddress, employeeRequest.maxCapacity(), employeeRequest.isDriver(), user);
            employeeRepository.save(employee);
        }
    }
}
