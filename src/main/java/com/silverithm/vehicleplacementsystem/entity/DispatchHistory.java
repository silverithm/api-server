package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
@Entity
public class DispatchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String dispatchResult; // JSON 형태로 저장

    private int totalEmployees;
    private int totalElders;

    public static DispatchHistory of(LocalDateTime createdAt, String dispatchResult, int totalEmployees, int totalElders) {
        DispatchHistory dispatchHistory = new DispatchHistory();
        dispatchHistory.createdAt = createdAt;
        dispatchHistory.dispatchResult = dispatchResult;
        dispatchHistory.totalEmployees = totalEmployees;
        dispatchHistory.totalElders = totalElders;
        return dispatchHistory;
    }
}