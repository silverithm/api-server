package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.util.List;

/** 본인부담금 청구 요청·응답. 비급여 합계·청구액·상태는 응답에만 있고 서버가 계산한다. */
public final class LabCopayBillDto {

    private LabCopayBillDto() {
    }

    public record NonCoveredItem(String label, Integer amount) {
    }

    public record Input(Long elderlyId, String billMonth, String careGrade, String copayClass,
                        String daycareUnit, String hourBand, Integer days, Integer dailyFee,
                        Integer coveredTotal, Integer copayAmount, Integer overLimitAmount,
                        List<NonCoveredItem> nonCoveredItems, Integer previousUnpaid) {
    }

    public record PaymentInput(Integer amount, LocalDate paidOn, String payerName) {
    }

    public record View(Long id, Long elderlyId, String elderlyName, String billMonth, String careGrade,
                       String copayClass, String daycareUnit, String hourBand, int days, int dailyFee,
                       int coveredTotal, int copayAmount, int overLimitAmount,
                       List<NonCoveredItem> nonCoveredItems, int nonCoveredAmount, int previousUnpaid,
                       int totalDue, int paidAmount, LocalDate lastPaidOn, String payerName, String status) {
    }
}
