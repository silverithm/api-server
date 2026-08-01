package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.service.DemoProvisioningService;
import com.silverithm.vehicleplacementsystem.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 체험하기(데모) 진입 엔드포인트. 비로그인 방문자가 호출하면 격리된 데모 테넌트를
 * 즉석 생성하고 signin과 동일한 응답을 반환한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final DemoProvisioningService demoProvisioningService;
    private final RedisUtils redisUtils;

    @PostMapping("/api/v1/demo/start")
    public SigninResponseDTO startDemo(HttpServletRequest request) {
        String clientIp = ClientIp.from(request);

        if (redisUtils.isExceededDemoStartIpLimit(clientIp)) {
            log.warn("[Demo] IP 요청 한도 초과: {}", clientIp);
            throw new CustomException("체험 요청이 많습니다. 잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (redisUtils.isExceededDemoStartGlobalLimit()) {
            log.warn("[Demo] 일일 전체 생성 한도 초과 (요청 IP: {})", clientIp);
            throw new CustomException("오늘 체험 신청이 마감되었습니다. 내일 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS);
        }

        return demoProvisioningService.provisionDemoTenant();
    }
}
