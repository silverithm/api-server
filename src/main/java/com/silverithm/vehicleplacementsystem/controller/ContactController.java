package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.ContactInquiryRequest;
import com.silverithm.vehicleplacementsystem.service.ContactInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 페이지(문의하기·제휴 광고) 문의 접수. 로그인 없이 호출된다.
 */
@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactInquiryService contactInquiryService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody ContactInquiryRequest request,
                                                      HttpServletRequest httpRequest) {
        contactInquiryService.submit(request, resolveClientIp(httpRequest));
        return ResponseEntity.ok(Map.of("success", true, "message", "문의가 접수되었습니다."));
    }

    /** nginx 뒤에 있어 원본 IP는 X-Forwarded-For 첫 값에 있다. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
