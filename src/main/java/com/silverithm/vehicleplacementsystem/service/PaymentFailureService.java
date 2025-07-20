package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentFailureResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureLog;
import com.silverithm.vehicleplacementsystem.entity.PaymentFailureReason;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionType;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.PaymentFailureLogRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFailureService {
    
    private final PaymentFailureLogRepository paymentFailureLogRepository;
    private final UserRepository userRepository;
    
    public Page<PaymentFailureResponseDTO> getMyPaymentFailures(UserDetails userDetails, Pageable pageable) {
        AppUser user = findUserByEmail(userDetails.getUsername());
        
        Page<PaymentFailureLog> failures = paymentFailureLogRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        
        return failures.map(PaymentFailureResponseDTO::new);
    }
    
    public Page<PaymentFailureResponseDTO> getMyPaymentFailuresByReason(UserDetails userDetails, 
                                                                         PaymentFailureReason reason, 
                                                                         Pageable pageable) {
        AppUser user = findUserByEmail(userDetails.getUsername());
        
        Page<PaymentFailureLog> failures = paymentFailureLogRepository
                .findByUserIdAndFailureReasonOrderByCreatedAtDesc(user.getId(), reason, pageable);
        
        return failures.map(PaymentFailureResponseDTO::new);
    }
    
    public Page<PaymentFailureResponseDTO> getMyPaymentFailuresByDateRange(UserDetails userDetails,
                                                                            LocalDate startDate,
                                                                            LocalDate endDate,
                                                                            Pageable pageable) {
        AppUser user = findUserByEmail(userDetails.getUsername());
        
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        
        Page<PaymentFailureLog> failures = paymentFailureLogRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(user.getId(), startDateTime, endDateTime, pageable);
        
        return failures.map(PaymentFailureResponseDTO::new);
    }
    
    @Transactional
    public void savePaymentFailure(AppUser user, Long subscriptionId, PaymentFailureReason reason, 
                                   String failureMessage, Integer attemptedAmount, 
                                   SubscriptionType subscriptionType, SubscriptionBillingType billingType, 
                                   String paymentGatewayResponse) {
        
        PaymentFailureLog failureLog = PaymentFailureLog.builder()
                .user(user)
                .subscriptionId(subscriptionId)
                .failureReason(reason)
                .failureMessage(failureMessage)
                .attemptedAmount(attemptedAmount)
                .subscriptionType(subscriptionType)
                .billingType(billingType)
                .paymentGatewayResponse(paymentGatewayResponse)
                .build();
        
        paymentFailureLogRepository.save(failureLog);
        log.info("Payment failure logged for user: {}, reason: {}", user.getEmail(), reason);
    }
    
    @Transactional
    public void savePaymentFailure(String userEmail, Long subscriptionId, PaymentFailureReason reason, 
                                   String failureMessage, Integer attemptedAmount, 
                                   SubscriptionType subscriptionType, SubscriptionBillingType billingType, 
                                   String paymentGatewayResponse) {
        
        AppUser user = findUserByEmail(userEmail);
        savePaymentFailure(user, subscriptionId, reason, failureMessage, attemptedAmount, 
                          subscriptionType, billingType, paymentGatewayResponse);
    }
    
    private AppUser findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found with email: " + email, HttpStatus.NOT_FOUND));
    }
}