package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.EmployeeUpdateRequestDTO;
import java.io.InputStream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ExcelService {

    @Autowired
    private EmployeeService employeeService;

    @Transactional
    public void uploadExcel(InputStream file) throws Exception {

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

                if (id.equals(0)) {
                    // create
                    employeeService.addEmployee(1L, new AddEmployeeRequest(
                            name, workPlaceName, homeAddressName, maximumCapacity, id
                    ));
                } else {
                    employeeService.updateEmployee(1L,
                            new EmployeeUpdateRequestDTO(name, homeAddressName, workPlaceName, maximumCapacity));
                }


            }

//            for (Row row : sheet) {
//                double idCell = row.getCell(0).getNumericCellValue();
//                Long id = (long) idCell;
//                String name = row.getCell(1).getStringCellValue();
//                String homeAddressName = row.getCell(2).getStringCellValue();
//                String workPlaceName = "경상남도 진주시 주약약골길 86";
//                int maximumCapacity = (int) row.getCell(3).getNumericCellValue();
//
//                if (id.equals(0)) {
//                    // create
//
//                    this.addEmployee(1L, new AddEmployeeRequest(
//                            name, workPlaceName, homeAddressName, maximumCapacity, id
//                    ));
//
//                } else {
//                    // update
//                    this.updateEmployee(1L,
//                            new EmployeeUpdateRequestDTO(name, homeAddressName, workPlaceName, maximumCapacity));
//                }
//
//            }
        }
    }
}
