package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabMealPlanDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabMealPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 주간 식단표 API. */
@RestController
@RequestMapping("/api/v1/lab/meal-plans")
@RequiredArgsConstructor
public class LabMealPlanController {

    private final LabMealPlanService service;

    /** weekStart면 그 주 한 건(없으면 404), month면 그 달에 걸친 주 목록. 둘 다 주거나 둘 다 없으면 400 */
    @GetMapping
    public ResponseEntity<?> get(@RequestParam(required = false) String weekStart,
                                 @RequestParam(required = false) String month) {
        if (weekStart != null && month != null) {
            throw new CustomException("weekStart와 month는 함께 보낼 수 없습니다. 하나만 보내 주세요", HttpStatus.BAD_REQUEST);
        }
        if (weekStart != null) {
            return ResponseEntity.ok(service.getByWeek(weekStart));
        }
        if (month != null) {
            List<LabMealPlanDto.View> list = service.listByMonth(month);
            return ResponseEntity.ok(list);
        }
        throw new CustomException("weekStart 또는 month 중 하나를 보내 주세요", HttpStatus.BAD_REQUEST);
    }

    @PutMapping
    public LabMealPlanDto.View save(@RequestBody LabMealPlanDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
