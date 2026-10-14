package com.silverithm.vehicleplacementsystem.lab.controller;

import com.silverithm.vehicleplacementsystem.lab.dto.LabAccessDto;
import com.silverithm.vehicleplacementsystem.lab.service.LabAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실험실 사용 가능 여부 — 로그인한 누구나 물을 수 있고 항상 200이다.
 * 웹은 이 값으로 메뉴를 보일지 정하고, AI 라우트도 같은 값으로 접근을 확인한다.
 */
@RestController
@RequestMapping("/api/v1/lab/access")
@RequiredArgsConstructor
public class LabAccessController {

    private final LabAccessService labAccessService;

    @GetMapping
    public LabAccessDto access() {
        return new LabAccessDto(labAccessService.isEnabled());
    }
}
