package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 직원/어르신 명단 엑셀 입출력.
 *
 * <p>어르신·직원의 성명과 자택 주소는 개인정보이므로 모든 진입점은 인증된 사용자의
 * 소속 기관(company)으로 범위를 제한한다. 타 기관 데이터는 조회·수정 대상에서 제외된다.
 */
@Service
@Slf4j
public class ExcelService {

    private final EmployeeService employeeService;
    private final ElderService elderService;
    private final ElderRepository elderRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;


    public ExcelService(EmployeeService employeeService, ElderService elderService, ElderRepository elderRepository,
                        EmployeeRepository employeeRepository, UserRepository userRepository) {
        this.employeeService = employeeService;
        this.elderService = elderService;
        this.elderRepository = elderRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void uploadEmployeeExcel(UserDetails userDetails, InputStream file) throws Exception {
        AppUser requester = resolveRequester(userDetails);
        Long companyId = requireCompanyId(requester);
        String workPlaceName = requireCompanyAddress(requester);

        Workbook workbook = new XSSFWorkbook(file);

        int created = 0;
        int updated = 0;

        for (Sheet sheet : workbook) {

            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {

                double idCell = sheet.getRow(i).getCell(0).getNumericCellValue();
                Long id = (long) idCell;

                String name = "";
                if (sheet.getRow(i).getCell(1) != null) {
                    name = sheet.getRow(i).getCell(1).getStringCellValue();
                }

                String homeAddressName = "";
                if (sheet.getRow(i).getCell(2) != null) {
                    homeAddressName = sheet.getRow(i).getCell(2).getStringCellValue();
                }

                int maximumCapacity = 0;
                if (sheet.getRow(i).getCell(4) != null) {
                    maximumCapacity = (int) sheet.getRow(i).getCell(4).getNumericCellValue();
                }

                Boolean isDriver = false;
                if (sheet.getRow(i).getCell(5) != null) {
                    isDriver = sheet.getRow(i).getCell(5).getBooleanCellValue();
                }

                if (id.equals(0L)) {
                    // create — 요청자 본인 계정/기관 소속으로 생성
                    employeeService.addEmployee(requester.getId(), new AddEmployeeRequest(
                            name, workPlaceName, homeAddressName, maximumCapacity, isDriver
                    ));
                    created++;
                } else {
                    requireEmployeeInCompany(id, companyId);
                    employeeService.updateEmployee(id,
                            new EmployeeUpdateRequestDTO(name, homeAddressName, workPlaceName, maximumCapacity,
                                    isDriver));
                    updated++;
                }

            }
        }

        log.info("[Excel] 직원 업로드 완료: companyId={}, 생성={}건, 수정={}건", companyId, created, updated);
    }

    @Transactional
    public void uploadElderlyExcel(UserDetails userDetails, InputStream file) throws Exception {
        AppUser requester = resolveRequester(userDetails);
        Long companyId = requireCompanyId(requester);

        Workbook workbook = new XSSFWorkbook(file);

        int created = 0;
        int updated = 0;

        for (Sheet sheet : workbook) {

            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {

                double idCell = sheet.getRow(i).getCell(0).getNumericCellValue();
                Long id = (long) idCell;

                String name = "";
                if (sheet.getRow(i).getCell(1) != null) {
                    name = sheet.getRow(i).getCell(1).getStringCellValue();
                }

                String homeAddressName = "";
                if (sheet.getRow(i).getCell(2) != null) {
                    homeAddressName = sheet.getRow(i).getCell(2).getStringCellValue();
                }

                Boolean requiredFrontSeat = false;
                if (sheet.getRow(i).getCell(3) != null) {
                    requiredFrontSeat = sheet.getRow(i).getCell(3).getBooleanCellValue();
                }

                if (id.equals(0L)) {
                    // create — 요청자 본인 계정/기관 소속으로 생성
                    elderService.addElder(requester.getId(), new AddElderRequest(
                            name, homeAddressName, requiredFrontSeat
                    ));
                    created++;
                } else {
                    requireElderInCompany(id, companyId);
                    elderService.updateElder(id,
                            new ElderUpdateRequestDTO(name, homeAddressName, requiredFrontSeat));
                    updated++;
                }

            }
        }

        log.info("[Excel] 어르신 업로드 완료: companyId={}, 생성={}건, 수정={}건", companyId, created, updated);
    }

    @Transactional(readOnly = true)
    public Workbook downloadElderlyExcel(UserDetails userDetails) {
        Long companyId = requireCompanyId(resolveRequester(userDetails));

        Workbook workbook = new XSSFWorkbook();
        Sheet elderlySheet = workbook.createSheet("어르신");
        int rowNo = 0;

        Row headerRow = elderlySheet.createRow(rowNo++);
        headerRow.createCell(0).setCellValue("아이디");
        headerRow.createCell(1).setCellValue("이름");
        headerRow.createCell(2).setCellValue("집주소");
        headerRow.createCell(3).setCellValue("앞자리여부");

        List<Elderly> elderlys = elderRepository.findAllInCompanyScope(companyId);
        // 이름은 암호화 컬럼이라 DB 정렬이 안 된다 — 복호화된 값으로 정렬한다
        elderlys.sort(java.util.Comparator.comparing(Elderly::getName,
                java.util.Comparator.nullsLast(String::compareTo)));

        for (Elderly elderly : elderlys) {
            Row elderlyRow = elderlySheet.createRow(rowNo++);
            elderlyRow.createCell(0).setCellValue(elderly.getId());
            elderlyRow.createCell(1).setCellValue(elderly.getName());
            elderlyRow.createCell(2).setCellValue(elderly.getHomeAddressName());
            elderlyRow.createCell(3).setCellValue(elderly.isRequiredFrontSeat());
        }

        log.info("[Excel] 어르신 명단 다운로드: companyId={}, {}건", companyId, elderlys.size());
        return workbook;
    }

    @Transactional(readOnly = true)
    public Workbook downloadEmployeeExcel(UserDetails userDetails) {
        Long companyId = requireCompanyId(resolveRequester(userDetails));

        Workbook workbook = new XSSFWorkbook();
        Sheet employeeSheet = workbook.createSheet("직원");
        int rowNo = 0;

        Row headerRow = employeeSheet.createRow(rowNo++);
        headerRow.createCell(0).setCellValue("아이디");
        headerRow.createCell(1).setCellValue("이름");
        headerRow.createCell(2).setCellValue("집주소");
        headerRow.createCell(3).setCellValue("직장주소");
        headerRow.createCell(4).setCellValue("최대인원");

        List<Employee> employees = employeeRepository.findAllInCompanyScope(companyId);
        // 이름은 암호화 컬럼이라 DB 정렬이 안 된다 — 복호화된 값으로 정렬한다
        employees.sort(java.util.Comparator.comparing(Employee::getName,
                java.util.Comparator.nullsLast(String::compareTo)));

        for (Employee employee : employees) {

            Row employeeRow = employeeSheet.createRow(rowNo++);
            employeeRow.createCell(0).setCellValue(employee.getId());
            employeeRow.createCell(1).setCellValue(employee.getName());
            employeeRow.createCell(2).setCellValue(employee.getHomeAddressName());
            employeeRow.createCell(3).setCellValue(
                    employee.getCompany() != null ? employee.getCompany().getAddressName() : "");
            employeeRow.createCell(4).setCellValue(employee.getMaximumCapacity());

        }

        log.info("[Excel] 직원 명단 다운로드: companyId={}, {}건", companyId, employees.size());
        return workbook;
    }

    // ─── 접근 범위 검증 ───

    private AppUser resolveRequester(UserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException("인증 정보가 없습니다", HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
    }

    private Long requireCompanyId(AppUser requester) {
        Company company = requester.getCompany();
        if (company == null || company.getId() == null) {
            throw new CustomException("소속 기관이 없는 계정입니다", HttpStatus.FORBIDDEN);
        }
        return company.getId();
    }

    private String requireCompanyAddress(AppUser requester) {
        Company company = requester.getCompany();
        String addressName = company != null ? company.getAddressName() : null;
        if (addressName == null || addressName.isBlank()) {
            throw new CustomException("기관 주소가 등록되어 있지 않습니다", HttpStatus.BAD_REQUEST);
        }
        return addressName;
    }

    private void requireEmployeeInCompany(Long employeeId, Long companyId) {
        if (!employeeRepository.existsInCompanyScope(employeeId, companyId)) {
            log.warn("[Excel] 타 기관 직원 수정 시도 차단: employeeId={}, companyId={}", employeeId, companyId);
            throw new CustomException("해당 직원에 대한 권한이 없습니다", HttpStatus.FORBIDDEN);
        }
    }

    private void requireElderInCompany(Long elderId, Long companyId) {
        if (!elderRepository.existsInCompanyScope(elderId, companyId)) {
            log.warn("[Excel] 타 기관 어르신 수정 시도 차단: elderId={}, companyId={}", elderId, companyId);
            throw new CustomException("해당 어르신에 대한 권한이 없습니다", HttpStatus.FORBIDDEN);
        }
    }
}
