package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabNursingLogDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabNursingLogService;
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

/** 간호일지 API. 기관은 요청자에게서 서버가 직접 해석하므로 기관 ID 파라미터가 없다. */
@RestController
@RequestMapping("/api/v1/lab/nursing-logs")
@RequiredArgsConstructor
public class LabNursingLogController {

    private final LabNursingLogService service;

    /** date 하나면 그날 전체, elderlyId+month면 한 어르신의 한 달 */
    @GetMapping
    public List<LabNursingLogDto.View> list(@RequestParam(required = false) String date,
                                            @RequestParam(required = false) Long elderlyId,
                                            @RequestParam(required = false) String month) {
        if (date != null) {
            return service.listByDate(date);
        }
        if (elderlyId != null && month != null) {
            return service.listByElderAndMonth(elderlyId, month);
        }
        throw new CustomException("date 또는 elderlyId와 month를 함께 보내 주세요", HttpStatus.BAD_REQUEST);
    }

    @PutMapping
    public LabNursingLogDto.View save(@RequestBody LabNursingLogDto.Input input) {
        return service.upsert(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
