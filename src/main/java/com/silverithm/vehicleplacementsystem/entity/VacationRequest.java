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

    @Column(nullable = false, length = 100)
    private String role;

    @Column(length = 500)
    private String reason;

    @Column
    private String userId;

    // 휴무 종류: regular(일반) / mandatory(필수) / substitute(대체휴무).
    // 레거시 데이터에는 '휴가', 'admin_created' 등도 존재하므로 enum이 아닌 문자열로 둔다.
    @Column
    private String type;

    // 연차를 사용하지 않는 휴무의 세부 유형: personal, sick, emergency, family, other, substitute
    @Column(length = 50)
    private String vacationType;

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

    // 휴무 종류 상수 (type 컬럼 값)
    public static final String TYPE_REGULAR = "regular";
    public static final String TYPE_MANDATORY = "mandatory";
    public static final String TYPE_SUBSTITUTE = "substitute";

    public static boolean isSubstituteType(String type) {
        return TYPE_SUBSTITUTE.equalsIgnoreCase(type == null ? null : type.trim());
    }

    /**
     * 대체휴무 여부. 일반/필수 휴무와 동일하게 동작하며 표시상 구분만 다르다.
     */
    public boolean isSubstitute() {
        return isSubstituteType(this.type) || isSubstituteType(this.vacationType);
    }

    public enum VacationDuration {
        FULL_DAY("연차", "하루 종일", 1.0),
        HALF_DAY_AM("오전 반차", "오전 반일", 0.5),
        HALF_DAY_PM("오후 반차", "오후 반일", 0.5),
        UNUSED("미사용", "미사용", 0.0);

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

    public static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "caregiver";
        }

        String trimmedRole = role.trim();
        return switch (trimmedRole.toUpperCase()) {
            case "CAREGIVER", "ROLE_CAREGIVER", "요양보호사" -> "caregiver";
            case "OFFICE", "ROLE_OFFICE", "사무직" -> "office";
            case "ALL" -> "all";
            case "ADMIN", "ROLE_ADMIN", "관리자" -> "admin";
            case "EMPLOYEE", "ROLE_EMPLOYEE" -> "employee";
            default -> trimmedRole;
        };
    }

    public String getNormalizedRole() {
        return normalizeRole(this.role);
    }
}
