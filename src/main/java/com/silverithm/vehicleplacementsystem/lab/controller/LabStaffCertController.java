package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffHrDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabStaffHrService;
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

/** 직원 이수·검진 기록 API. */
@RestController
@RequestMapping("/api/v1/lab/staff-certs")
@RequiredArgsConstructor
public class LabStaffCertController {

    private final LabStaffHrService service;

    /** memberId 또는 year(또는 둘 다)로 거른다 — 둘 다 없으면 400 */
    @GetMapping
    public List<LabStaffHrDto.CertView> list(@RequestParam(required = false) Long memberId,
                                             @RequestParam(required = false) String year) {
        return service.listCerts(memberId, year);
    }

    @PostMapping
    public LabStaffHrDto.CertView create(@RequestBody LabStaffHrDto.CertInput input) {
        return service.createCert(input);
    }

    @PutMapping("/{id}")
    public LabStaffHrDto.CertView update(@PathVariable Long id, @RequestBody LabStaffHrDto.CertInput input) {
        return service.updateCert(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCert(id);
        return ResponseEntity.noContent().build();
    }
}
