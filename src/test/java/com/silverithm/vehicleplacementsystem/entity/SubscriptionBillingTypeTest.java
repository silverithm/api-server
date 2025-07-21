package com.silverithm.vehicleplacementsystem.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("구독 결제 유형 테스트")
class SubscriptionBillingTypeTest {

    @Test
    @DisplayName("월간 구독 - 현재 시간 기준 endDate 계산")
    void calculateEndDate_Monthly_FromNow() {
        // given
        LocalDateTime before = LocalDateTime.now();
        
        // when
        LocalDateTime endDate = SubscriptionBillingType.calculateEndDate(SubscriptionBillingType.MONTHLY);
        
        // then
        LocalDateTime after = LocalDateTime.now();
        assertTrue(endDate.isAfter(before.plusMonths(1).minusSeconds(1)));
        assertTrue(endDate.isBefore(after.plusMonths(1).plusSeconds(1)));
    }

    @Test
    @DisplayName("연간 구독 - 현재 시간 기준 endDate 계산")
    void calculateEndDate_Yearly_FromNow() {
        // given
        LocalDateTime before = LocalDateTime.now();
        
        // when
        LocalDateTime endDate = SubscriptionBillingType.calculateEndDate(SubscriptionBillingType.YEARLY);
        
        // then
        LocalDateTime after = LocalDateTime.now();
        assertTrue(endDate.isAfter(before.plusYears(1).minusSeconds(1)));
        assertTrue(endDate.isBefore(after.plusYears(1).plusSeconds(1)));
    }

    @Test
    @DisplayName("월간 구독 - 기존 endDate에서 연장")
    void extendEndDate_Monthly_FromExistingDate() {
        // given
        LocalDateTime currentEndDate = LocalDateTime.of(2025, 7, 15, 10, 30, 0);
        
        // when
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(SubscriptionBillingType.MONTHLY, currentEndDate);
        
        // then
        LocalDateTime expectedEndDate = LocalDateTime.of(2025, 8, 15, 10, 30, 0);
        assertEquals(expectedEndDate, extendedEndDate);
    }

    @Test
    @DisplayName("연간 구독 - 기존 endDate에서 연장")
    void extendEndDate_Yearly_FromExistingDate() {
        // given
        LocalDateTime currentEndDate = LocalDateTime.of(2025, 7, 15, 10, 30, 0);
        
        // when
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(SubscriptionBillingType.YEARLY, currentEndDate);
        
        // then
        LocalDateTime expectedEndDate = LocalDateTime.of(2026, 7, 15, 10, 30, 0);
        assertEquals(expectedEndDate, extendedEndDate);
    }

    @Test
    @DisplayName("월말 날짜 처리 - 2월 말일에서 연장")
    void extendEndDate_Monthly_EndOfFebruary() {
        // given - 2월 28일
        LocalDateTime currentEndDate = LocalDateTime.of(2025, 2, 28, 15, 45, 30);
        
        // when
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(SubscriptionBillingType.MONTHLY, currentEndDate);
        
        // then - 3월 28일
        LocalDateTime expectedEndDate = LocalDateTime.of(2025, 3, 28, 15, 45, 30);
        assertEquals(expectedEndDate, extendedEndDate);
    }

    @Test
    @DisplayName("윤년 처리 - 2월 29일에서 연장")
    void extendEndDate_Monthly_LeapYear() {
        // given - 윤년 2024년 2월 29일
        LocalDateTime currentEndDate = LocalDateTime.of(2024, 2, 29, 12, 0, 0);
        
        // when
        LocalDateTime extendedEndDate = SubscriptionBillingType.extendEndDate(SubscriptionBillingType.MONTHLY, currentEndDate);
        
        // then - 3월 29일 (2월 29일 + 1개월)
        LocalDateTime expectedEndDate = LocalDateTime.of(2024, 3, 29, 12, 0, 0);
        assertEquals(expectedEndDate, extendedEndDate);
    }
}