package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabProgramPlanDto;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.service.LabProgramPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 월간 프로그램 계획표 API. */
@RestController
@RequestMapping("/api/v1/lab/program-schedules")
@RequiredArgsConstructor
public class LabProgramScheduleController {

    private final LabProgramPlanService service;

    /** month면 그 달 한 건(없으면 404), year면 그 해 목록. 둘 다 주거나 둘 다 없으면 400 */
    @GetMapping
    public ResponseEntity<?> get(@RequestParam(required = false) String month,
                                 @RequestParam(required = false) String year) {
        if (month != null && year != null) {
            throw new CustomException("month와 year는 함께 보낼 수 없습니다. 하나만 보내 주세요", HttpStatus.BAD_REQUEST);
        }
        if (month != null) {
            return ResponseEntity.ok(service.getSchedule(month));
        }
        if (year != null) {
            List<LabProgramPlanDto.ScheduleView> list = service.listSchedules(year);
            return ResponseEntity.ok(list);
        }
        throw new CustomException("month 또는 year 중 하나를 보내 주세요", HttpStatus.BAD_REQUEST);
    }

    /** 달로 upsert */
    @PutMapping
    public LabProgramPlanDto.ScheduleView save(@RequestBody LabProgramPlanDto.ScheduleInput input) {
        return service.upsertSchedule(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}
