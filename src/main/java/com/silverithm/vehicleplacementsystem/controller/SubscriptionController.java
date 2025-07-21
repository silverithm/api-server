package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.PaymentFailureResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.service.BillingService;
import com.silverithm.vehicleplacementsystem.service.PaymentFailureService;
import com.silverithm.vehicleplacementsystem.service.SubscriptionService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {
    private final SubscriptionService subscriptionService;
    private final BillingService billingService;
    private final PaymentFailureService paymentFailureService;

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

    @PutMapping("/activate")
    public ResponseEntity<SubscriptionResponseDTO> activateSubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.activateSubscription(userDetails));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SubscriptionResponseDTO>> getActiveSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getActiveSubscriptions());
    }

    @GetMapping("/payment-failures")
    public ResponseEntity<Page<PaymentFailureResponseDTO>> getMyPaymentFailures(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(paymentFailureService.getMyPaymentFailures(userDetails, pageable));
    }

    @GetMapping("/payment-failures/by-reason")
    public ResponseEntity<Page<PaymentFailureResponseDTO>> getMyPaymentFailuresByReason(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam PaymentFailureReason reason,
            Pageable pageable) {
        return ResponseEntity.ok(paymentFailureService.getMyPaymentFailuresByReason(userDetails, reason, pageable));
    }

    @GetMapping("/payment-failures/by-date")
    public ResponseEntity<Page<PaymentFailureResponseDTO>> getMyPaymentFailuresByDateRange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        return ResponseEntity.ok(paymentFailureService.getMyPaymentFailuresByDateRange(userDetails, startDate, endDate, pageable));
    }
}