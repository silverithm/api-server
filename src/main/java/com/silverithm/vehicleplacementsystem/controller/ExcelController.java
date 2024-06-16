package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.ExcelService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ExcelController {

    private final ExcelService excelService;

    public ExcelController(ExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/api/v1/employee/downloadEmployeeExcel")
    public void downloadEmployeeExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-Disposition", "attachment;filename=employee.xlsx");

        Workbook workbook = excelService.downloadEmployeeExcel();
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    @GetMapping("/api/v1/employee/downloadElderlyExcel")
    public void downloadElderlyExcel(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-Disposition", "attachment;filename=elderly.xlsx");

        Workbook workbook = excelService.downloadElderlyExcel();
        workbook.write(response.getOutputStream());
        workbook.close();
    }


    @PostMapping("/api/v1/employee/uploadEmployeeExcel")
    public void uploadEmployeeExcel(@RequestParam("file") MultipartFile file) throws Exception {
        excelService.uploadEmployeeExcel(file.getInputStream());
    }

    @PostMapping("/api/v1/employee/uploadElderlyExcel")
    public void uploadElderlyExcel(@RequestParam("file") MultipartFile file) throws Exception {
        excelService.uploadElderlyExcel(file.getInputStream());
    }

}
