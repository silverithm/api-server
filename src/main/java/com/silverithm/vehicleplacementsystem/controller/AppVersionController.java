package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AppVersionResponseDTO;
import com.silverithm.vehicleplacementsystem.service.AppVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/app-version")
public class AppVersionController {

    private final AppVersionService appVersionService;

    @GetMapping
    public ResponseEntity<AppVersionResponseDTO> getLatestAppVersion() {
        log.info("[App Version API] Retrieving latest app version information");
        AppVersionResponseDTO appVersion = appVersionService.getLatestAppVersion();
        return ResponseEntity.ok(appVersion);
    }
}