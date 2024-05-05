package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import java.io.IOException;
import java.io.InputStream;
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

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private ElderService elderService;

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

                if (id.equals(0L)) {
                    // create
                    employeeService.addEmployee(1L, new AddEmployeeRequest(
                            name, workPlaceName, homeAddressName, maximumCapacity
                    ));
                } else {
                    employeeService.updateEmployee(id,
                            new EmployeeUpdateRequestDTO(name, homeAddressName, workPlaceName, maximumCapacity));
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
                        elderService.addElder(1L, new AddElderRequest(
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
}

