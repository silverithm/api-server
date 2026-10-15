package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabTransportDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabTransportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

/** 송영 운행일지 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/transport-logs")
@RequiredArgsConstructor
public class LabTransportLogController {

    private final LabTransportService service;

    /** date 또는 month 중 하나 — 둘 다 없으면 400 */
    @GetMapping
    public List<LabTransportDto.LogView> list(@RequestParam(required = false) String date,
                                              @RequestParam(required = false) String month) {
        return service.listLogs(date, month);
    }

    /** runDate + direction + vehicleNo로 upsert */
    @PutMapping
    public LabTransportDto.LogView save(@RequestBody LabTransportDto.LogInput input) {
        return service.upsertLog(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteLog(id);
        return ResponseEntity.noContent().build();
    }
}
