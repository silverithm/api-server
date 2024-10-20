package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.EmployeeRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.jdbc.Work;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExcelService {

    private final EmployeeService employeeService;
    private final ElderService elderService;
    private final ElderRepository elderRepository;
    private final EmployeeRepository employeeRepository;


    public ExcelService(EmployeeService employeeService, ElderService elderService, ElderRepository elderRepository,
                        EmployeeRepository employeeRepository) {
        this.employeeService = employeeService;
        this.elderService = elderService;
        this.elderRepository = elderRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public void uploadEmployeeExcel(InputStream file) throws Exception {

        Workbook workbook = new XSSFWorkbook(file);

        for (Sheet sheet : workbook) {

            for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {

                double idCell = sheet.getRow(i).getCell(0).getNumericCellValue();
                Long id = (long) idCell;

                String name = "";
                if (sheet.getRow(i).getCell(1) != null) {
                    name = sheet.getRow(i).getCell(1).getStringCellValue();
                }
                log.info(name);

                String homeAddressName = "";
                if (sheet.getRow(i).getCell(2) != null) {
                    homeAddressName = sheet.getRow(i).getCell(2).getStringCellValue();
                }
                log.info(homeAddressName);

                String workPlaceName = "경상남도 진주시 주약약골길 86";
                log.info(workPlaceName);

                int maximumCapacity = 0;
                if (sheet.getRow(i).getCell(4) != null) {
                    maximumCapacity = (int) sheet.getRow(i).getCell(4).getNumericCellValue();
                }
                log.info(String.valueOf(maximumCapacity));

                Boolean isDriver = false;
                if (sheet.getRow(i).getCell(5) != null) {
                    isDriver = sheet.getRow(i).getCell(5).getBooleanCellValue();
                }
                log.info(String.valueOf(isDriver));

                if (id.equals(0L)) {
                    // create
                    employeeService.addEmployee(2L, new AddEmployeeRequest(
                            name, workPlaceName, homeAddressName, maximumCapacity, isDriver
                    ));
                } else {
                    employeeService.updateEmployee(id,
                            new EmployeeUpdateRequestDTO(name, homeAddressName, workPlaceName, maximumCapacity,
                                    isDriver));
                }

            }
        }
    }

    public void uploadElderlyExcel(InputStream file) throws Exception {
        try {
            Workbook workbook = new XSSFWorkbook(file);
            log.info(workbook.toString());
            for (Sheet sheet : workbook) {

                for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {

                    double idCell = sheet.getRow(i).getCell(0).getNumericCellValue();
                    Long id = (long) idCell;

                    log.info(id.toString());
                    String name = "";
                    if (sheet.getRow(i).getCell(1) != null) {
                        name = sheet.getRow(i).getCell(1).getStringCellValue();
                    }
                    log.info(name);

                    String homeAddressName = "";
                    if (sheet.getRow(i).getCell(2) != null) {
                        homeAddressName = sheet.getRow(i).getCell(2).getStringCellValue();
                    }
                    log.info(homeAddressName);

                    Boolean requiredFrontSeat = false;
                    if (sheet.getRow(i).getCell(3).getBooleanCellValue()) {
                        requiredFrontSeat = sheet.getRow(i).getCell(3).getBooleanCellValue();
                    }
                    log.info(String.valueOf(requiredFrontSeat));

                    if (id.equals(0L)) {
                        //create
                        elderService.addElder(2L, new AddElderRequest(
                                name, homeAddressName, requiredFrontSeat
                        ));
                    } else {
                        //update
                        log.info("update start");
                        elderService.updateElder(id,
                                new ElderUpdateRequestDTO(name, homeAddressName, requiredFrontSeat));
                        log.info("update end");
                    }

                    log.info("end");

                }


            }
        } catch (Exception e) {
            log.info("upload error");
            log.info(e.toString());
        }


    }

    public Workbook downloadElderlyExcel() {
        Workbook workbook = new XSSFWorkbook();
        Sheet elderlySheet = workbook.createSheet("어르신");
        int rowNo = 0;

        Row headerRow = elderlySheet.createRow(rowNo++);
        headerRow.createCell(0).setCellValue("아이디");
        headerRow.createCell(1).setCellValue("이름");
        headerRow.createCell(2).setCellValue("집주소");
        headerRow.createCell(3).setCellValue("앞자리여부");

        List<Elderly> elderlys = elderRepository.findAll();

        for (Elderly elderly : elderlys) {
            Row elderlyRow = elderlySheet.createRow(rowNo++);
            elderlyRow.createCell(0).setCellValue(elderly.getId());
            elderlyRow.createCell(1).setCellValue(elderly.getName());
            elderlyRow.createCell(2).setCellValue(elderly.getHomeAddressName());
            elderlyRow.createCell(3).setCellValue(elderly.isRequiredFrontSeat());
        }

        return workbook;
    }

    public Workbook downloadEmployeeExcel() {
        Workbook workbook = new XSSFWorkbook();
        Sheet employeeSheet = workbook.createSheet("직원");
        int rowNo = 0;

        Row headerRow = employeeSheet.createRow(rowNo++);
        headerRow.createCell(0).setCellValue("아이디");
        headerRow.createCell(1).setCellValue("이름");
        headerRow.createCell(2).setCellValue("집주소");
        headerRow.createCell(3).setCellValue("직장주소");
        headerRow.createCell(4).setCellValue("최대인원");

        List<Employee> employees = employeeRepository.findAll();

        for (Employee employee : employees) {

            Row employeeRow = employeeSheet.createRow(rowNo++);
            employeeRow.createCell(0).setCellValue(employee.getId());
            employeeRow.createCell(1).setCellValue(employee.getName());
            employeeRow.createCell(2).setCellValue(employee.getHomeAddressName());
            employeeRow.createCell(3).setCellValue(employee.getWorkPlaceAddressName());
            employeeRow.createCell(4).setCellValue(employee.getMaximumCapacity());

        }

        return workbook;
    }
}

