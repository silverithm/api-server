package com.silverithm.vehicleplacementsystem.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 블루그린 배포 헬스체크용 경량 엔드포인트 (인증 불필요).
 * deploy.sh가 유휴 색 컨테이너 기동 후 이 응답을 확인하고 트래픽을 전환한다.
 */
@RestController
public class HealthController {

    @Value("${DEPLOY_COLOR:unknown}")
    private String deployColor;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "color", deployColor
        ));
    }
}
