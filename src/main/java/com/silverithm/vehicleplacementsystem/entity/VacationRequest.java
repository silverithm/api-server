package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "vacation_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(length = 500)
    private String reason;

    @Column
    private String userId;

    @Column
    private String type;

    @Column(nullable = false)
    @Builder.Default
    private String duration = "FULL_DAY";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // duration 문자열을 enum으로 변환하는 헬퍼 메서드
    public VacationDuration getDurationEnum() {
        try {
            return VacationDuration.valueOf(this.duration);
        } catch (IllegalArgumentException e) {
            return VacationDuration.FULL_DAY;
        }
    }

    // enum을 문자열로 설정하는 헬퍼 메서드
    public void setDurationEnum(VacationDuration duration) {
        this.duration = duration.name();
    }

    public enum VacationStatus {
        PENDING, APPROVED, REJECTED
    }

    public enum Role {
        CAREGIVER, OFFICE, ALL
    }

    public enum VacationDuration {
        FULL_DAY("연차", "하루 종일", 1.0),
        HALF_DAY_AM("오전 반차", "오전 반일", 0.5),
        HALF_DAY_PM("오후 반차", "오후 반일", 0.5);

        private final String displayName;
        private final String description;
        private final double days;

        VacationDuration(String displayName, String description, double days) {
            this.displayName = displayName;
            this.description = description;
            this.days = days;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public double getDays() {
            return days;
        }
    }
} 