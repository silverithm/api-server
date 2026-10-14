package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabBankDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabBankService;
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
import org.springframework.web.bind.annotation.RestController;

/** 통장 API — 계좌번호는 끝 4자리만 오간다. */
@RestController
@RequestMapping("/api/v1/lab/bank-accounts")
@RequiredArgsConstructor
public class LabBankAccountController {

    private final LabBankService service;

    @GetMapping
    public List<LabBankDto.AccountView> list() {
        return service.listAccounts();
    }

    @PostMapping
    public LabBankDto.AccountView create(@RequestBody LabBankDto.AccountInput input) {
        return service.createAccount(input);
    }

    @PutMapping("/{id}")
    public LabBankDto.AccountView update(@PathVariable Long id, @RequestBody LabBankDto.AccountInput input) {
        return service.updateAccount(id, input);
    }

    /** 월말 잔액 기록이 있으면 409 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }
}
