package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PaymentResponse;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public SubscriptionScheduler(SubscriptionService subscriptionService, UserRepository userRepository) {
        this.subscriptionService = subscriptionService;
        this.userRepository = userRepository;
    }


    //    @Scheduled(cron = "*/10 * * * * *")
    @Scheduled(cron = "0 0 6 * * *")
    public void processScheduledPayments() {
        LocalDateTime currentDate = LocalDateTime.now();
        log.info("🔄 스케줄러 실행됨 - 현재 시간: {}", currentDate);  // 스케줄러 실행 여부 확인
        List<AppUser> users = userRepository.findUsersRequiringSubscriptionBilling(currentDate);
        log.info("🔍 결제 대상 유저 수: {}", users.size());  // 결제 대상 유저 확인

        for (AppUser user : users) {

            SubscriptionRequestDTO requestDto = SubscriptionRequestDTO.of(
                    user.getSubscription().getPlanName(),
                    user.getSubscription().getBillingType(),
                    user.getSubscription().getAmount(),
                    user.getCustomerKey(),
                    user.getBillingKey(),
                    user.getSubscription().getPlanName().name() + "_" + user.getSubscription().getBillingType().name(),
                    user.getEmail(),
                    user.getUsername(),
                    0
            );

            PaymentResponse paymentResponse = subscriptionService.requestPayment(requestDto, user.getBillingKey());
//            log.info("스케줄링 결제 성공: " + user.getUsername() + "," + currentDate);
//            log.info(requestDto.toString());

            if (paymentResponse.status().equals("DONE")) {
                log.info("스케줄링 결제 성공: " + user.getUsername() + "," + currentDate);
            } else {
                log.info("스케줄링 결제 실패: " + user.getUsername() + "," + currentDate);
            }
        }
    }
}