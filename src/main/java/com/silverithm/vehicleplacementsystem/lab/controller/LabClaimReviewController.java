package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabClaimReviewDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabClaimReviewService;
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

/** 공단 청구 대조 API. 경로를 전부 적은 것은 batch가 같은 PUT이라 한눈에 구분하려는 것이다. */
@RestController
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabClaimReviewController {

    private final LabClaimReviewService service;

    /** month(YYYY-MM) 필수 */
    @GetMapping("/claim-reviews")
    public List<LabClaimReviewDto.View> list(@RequestParam(required = false) String month) {
        return service.list(month);
    }

    /** elderlyId + claimMonth로 덮어쓴다 — 상태는 서버가 정한다 */
    @PutMapping("/claim-reviews")
    public LabClaimReviewDto.View upsert(@RequestBody LabClaimReviewDto.Input input) {
        return service.upsert(input);
    }

    /** body {month, rows ≤200} — 전부 아니면 없음 */
    @PutMapping("/claim-reviews/batch")
    public List<LabClaimReviewDto.View> batch(@RequestBody LabClaimReviewDto.BatchInput input) {
        return service.batch(input);
    }

    @DeleteMapping("/claim-reviews/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
