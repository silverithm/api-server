package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.service.BillingService;
import com.silverithm.vehicleplacementsystem.service.SubscriptionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;

    @PostMapping
    public ResponseEntity<SubscriptionResponseDTO> createOrUpdateSubscription(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SubscriptionRequestDTO requestDto) {
        return ResponseEntity.ok(subscriptionService.createOrUpdateSubscription(userDetails, requestDto));
    }

    @PostMapping("/free")
    public ResponseEntity<SubscriptionResponseDTO> createFreeSubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.createFreeSubscription(userDetails));
    }

    @PostMapping("/admin/{userId}")
    public ResponseEntity<SubscriptionResponseDTO> createSubscriptionToUser(
            @RequestBody SubscriptionRequestDTO requestDto, @PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.createSubscriptionToUser(requestDto, userId));
    }


    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponseDTO> getSubscription(@PathVariable Long id) {
        return ResponseEntity.ok(subscriptionService.getSubscription(id));
    }

    @GetMapping()
    public ResponseEntity<SubscriptionResponseDTO> getMySubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.getMySubscription(userDetails));
    }

    @PutMapping("/cancel")
    public ResponseEntity<SubscriptionResponseDTO> cancelSubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(userDetails));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionResponseDTO>> getActiveSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getActiveSubscriptions());
    }


}