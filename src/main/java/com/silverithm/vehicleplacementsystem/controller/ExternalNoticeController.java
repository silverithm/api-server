package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ExternalNoticeDTO;
import com.silverithm.vehicleplacementsystem.service.ExternalNoticeCrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 노인장기요양보험(longtermcare.or.kr) 공지 - 조회 API (전 기관 공용, 인증 필요).
 */
@RestController
@RequestMapping("/api/v1/external-notices")
@RequiredArgsConstructor
public class ExternalNoticeController {

    private final ExternalNoticeCrawlerService externalNoticeCrawlerService;

    @GetMapping
    public ResponseEntity<Page<ExternalNoticeDTO>> getExternalNotices(
            @RequestParam(required = false) String source,
            Pageable pageable) {
        return ResponseEntity.ok(externalNoticeCrawlerService.getExternalNotices(source, pageable));
    }
}
