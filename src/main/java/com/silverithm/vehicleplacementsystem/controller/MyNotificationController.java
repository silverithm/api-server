package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.MyNotificationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 알림함 — 받는 사람은 토큰으로 정한다 */
@RestController
@RequestMapping("/api/v1/notifications/mine")
@RequiredArgsConstructor
public class MyNotificationController {

    private final MyNotificationService service;

    @GetMapping
    public Map<String, List<MyNotificationService.View>> unread(@RequestParam(required = false) Integer days) {
        return Map.of("notifications", service.unread(days));
    }

    public record ReadRequest(List<Long> ids) {
    }

    @PostMapping("/read")
    public Map<String, Integer> markRead(@RequestBody ReadRequest request) {
        return Map.of("updated", service.markRead(request == null ? null : request.ids()));
    }
}
