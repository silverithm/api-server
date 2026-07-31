package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.ExcelService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 직원/어르신 명단 엑셀 입출력 API.
 * 성명·자택주소가 포함되므로 인증이 필수이며, 조회·수정 범위는 요청자의 소속 기관으로 제한된다.
 */
@RestController
public class ExcelController {

    private final ExcelService excelService;

    public ExcelController(ExcelService excelService) {
        this.excelService = excelService;
    }

    @GetMapping("/api/v1/employee/downloadEmployeeExcel")
    public void downloadEmployeeExcel(@AuthenticationPrincipal UserDetails userDetails,
                                      HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-Disposition", "attachment;filename=employee.xlsx");

        Workbook workbook = excelService.downloadEmployeeExcel(userDetails);
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    @GetMapping("/api/v1/employee/downloadElderlyExcel")
    public void downloadElderlyExcel(@AuthenticationPrincipal UserDetails userDetails,
                                     HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-Disposition", "attachment;filename=elderly.xlsx");

        Workbook workbook = excelService.downloadElderlyExcel(userDetails);
        workbook.write(response.getOutputStream());
        workbook.close();
    }


    @PostMapping("/api/v1/employee/uploadEmployeeExcel")
    public void uploadEmployeeExcel(@AuthenticationPrincipal UserDetails userDetails,
                                    @RequestParam("file") MultipartFile file) throws Exception {
        excelService.uploadEmployeeExcel(userDetails, file.getInputStream());
    }

    @PostMapping("/api/v1/employee/uploadElderlyExcel")
    public void uploadElderlyExcel(@AuthenticationPrincipal UserDetails userDetails,
                                   @RequestParam("file") MultipartFile file) throws Exception {
        excelService.uploadElderlyExcel(userDetails, file.getInputStream());
    }

}
