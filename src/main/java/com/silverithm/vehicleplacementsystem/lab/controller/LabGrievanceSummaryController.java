package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabGrievanceDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabGrievanceSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 고충 요약 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/grievance-summary")
@RequiredArgsConstructor
public class LabGrievanceSummaryController {

    private final LabGrievanceSummaryService service;

    /** 그 해 접수된 고충의 건수만 — 제목·본문·작성자는 내보내지 않는다 */
    @GetMapping
    public LabGrievanceDto.Summary summary(@RequestParam(required = false) String year) {
        return service.summary(year);
    }
}
