package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabLedgerDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabLedgerService;
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

/** 현금출납부 API. */
@RestController
@RequestMapping("/api/v1/lab/ledger")
@RequiredArgsConstructor
public class LabLedgerController {

    private final LabLedgerService service;

    @GetMapping
    public List<LabLedgerDto.View> list(@RequestParam(required = false) String month,
                                        @RequestParam(required = false) String year) {
        return service.list(month, year);
    }

    @PostMapping
    public LabLedgerDto.View create(@RequestBody LabLedgerDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabLedgerDto.View update(@PathVariable Long id, @RequestBody LabLedgerDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
