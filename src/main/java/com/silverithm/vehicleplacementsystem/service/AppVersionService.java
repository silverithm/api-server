package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AppVersionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppVersion;
import com.silverithm.vehicleplacementsystem.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;

    public AppVersionResponseDTO getLatestAppVersion() {
        AppVersion appVersion = appVersionRepository.findTopByOrderByCreatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("앱 버전 정보를 찾을 수 없습니다"));
        
        return AppVersionResponseDTO.from(appVersion);
    }
}