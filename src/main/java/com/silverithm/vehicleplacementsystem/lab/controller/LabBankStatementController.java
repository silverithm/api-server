package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabBankDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBankService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 통장 월말 잔액 API. */
@RestController
@RequestMapping("/api/v1/lab/bank-statements")
@RequiredArgsConstructor
public class LabBankStatementController {

    private final LabBankService service;

    /** month 또는 accountId(또는 둘 다)로 거른다 — 둘 다 없으면 400 */
    @GetMapping
    public List<LabBankDto.StatementView> list(@RequestParam(required = false) String month,
                                               @RequestParam(required = false) Long accountId) {
        return service.listStatements(month, accountId);
    }

    /** accountId + statementMonth로 upsert */
    @PutMapping
    public LabBankDto.StatementView save(@RequestBody LabBankDto.StatementInput input) {
        return service.upsertStatement(input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteStatement(id);
        return ResponseEntity.noContent().build();
    }
}
