package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabDischargeReferralDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabDischargeReferralService;
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

/** 퇴소 연계기록지·제공대장 API. 같은 어르신·같은 퇴소일은 409. */
@RestController
@RequestMapping("/api/v1/lab/discharge-referrals")
@RequiredArgsConstructor
public class LabDischargeReferralController {

    private final LabDischargeReferralService service;

    /** year(YYYY, 퇴소일 기준), elderlyId로 좁힌다 — 퇴소일 최신순 */
    @GetMapping
    public List<LabDischargeReferralDto.View> list(@RequestParam(required = false) String year,
                                                   @RequestParam(required = false) Long elderlyId) {
        return service.list(year, elderlyId);
    }

    @PostMapping
    public LabDischargeReferralDto.View create(@RequestBody LabDischargeReferralDto.Input input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    public LabDischargeReferralDto.View update(@PathVariable Long id,
                                               @RequestBody LabDischargeReferralDto.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
