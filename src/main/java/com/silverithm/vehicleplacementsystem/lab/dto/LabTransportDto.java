package com.silverithm.vehicleplacementsystem.lab.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 송영(운행일지·차량·수칙/운행표 제공) 요청·응답. 시각은 JSON에서 'HH:mm' 문자열이다. */
public final class LabTransportDto {

    private LabTransportDto() {
    }

    // ---- 운행일지 ----

    public record RiderInput(Long elderlyId, Boolean boarded, String boardTime, String alightTime, String note) {
    }

    public record LogInput(LocalDate runDate, String direction, String vehicleNo, Long driverMemberId,
                           Long escortMemberId, String departTime, String arriveTime, List<RiderInput> riders,
                           String note) {
    }

    public record Rider(Long elderlyId, String elderlyName, boolean boarded, String boardTime, String alightTime,
                        String note) {
    }

    public record LogView(Long id, LocalDate runDate, String direction, String vehicleNo, Long driverMemberId,
                          String driverName, Long escortMemberId, String escortName, String departTime,
                          String arriveTime, List<Rider> riders, String note, String writerName,
                          LocalDateTime updatedAt) {
    }

    // ---- 차량·보험 ----

    public record VehicleInput(String vehicleNo, String label, String insurer, String policyNo,
                               LocalDate insuranceFrom, LocalDate insuranceTo, Boolean comprehensive,
                               Boolean active, String note) {
    }

    public record VehicleView(Long id, String vehicleNo, String label, String insurer, String policyNo,
                              LocalDate insuranceFrom, LocalDate insuranceTo, boolean comprehensive, boolean active,
                              String note, LocalDateTime updatedAt) {
    }

    // ---- 수칙·운행표 제공 ----

    public record DeliveryBatchInput(String kind, LocalDate deliveredOn, String method, String note,
                                     List<Long> elderlyIds) {
    }

    public record DeliveryView(Long id, Long elderlyId, String elderlyName, String kind, LocalDate deliveredOn,
                               String method, String note, LocalDateTime updatedAt) {
    }
}
