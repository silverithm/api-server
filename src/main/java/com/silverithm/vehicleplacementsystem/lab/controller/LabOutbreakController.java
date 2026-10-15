package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabOutbreakDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabOutbreakService;
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

/** 감염병 유행·발생 기록 API. 기관은 서버가 로그인 정보로 구한다. */
@RestController
@RequestMapping("/api/v1/lab/outbreaks")
@RequiredArgsConstructor
public class LabOutbreakController {

    private final LabOutbreakService service;

    @GetMapping
    public List<LabOutbreakDto.View> list(@RequestParam(required = false) String year) {
        return service.list(year);
    }

    @PostMapping
    public LabOutbreakDto.View create(@RequestBody LabOutbreakDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabOutbreakDto.View update(@PathVariable Long id, @RequestBody LabOutbreakDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
