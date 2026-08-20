package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 기본 일정 구분(회의·행사·교육·기타)의 기관별 커스터마이징.
 *
 * 기본 구분은 enum이라 지울 수 없고 기존 일정들이 물고 있으므로,
 * 기관별로 이름·색을 덮어쓰거나 등록 폼에서 숨기는 설정만 저장한다.
 * 행이 없거나 필드가 null이면 enum의 기본값({@link ScheduleCategory})을 쓴다.
 */
@Entity
@Table(name = "schedule_category_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_schedcat_company_category",
                columnNames = {"company_id", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleCategorySetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleCategory category;

    /** 기관이 바꾼 이름. null이면 기본 이름(회의 등)을 쓴다. */
    @Column(length = 50)
    private String displayName;

    /** 기관이 바꾼 색. null이면 enum 기본색을 쓴다. */
    @Column(length = 7)
    private String color;

    /** 새 일정 등록 폼에서 숨김. 기존 일정 표시는 그대로 유지된다. */
    @Column(nullable = false)
    private boolean hidden;

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

    /** 최종 표시 이름 (설정 → 기본 순 폴백) */
    public String effectiveName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : category.getDisplayName();
    }

    /** 최종 색 (설정 → 기본 순 폴백) */
    public String effectiveColor() {
        return (color != null && !color.isBlank()) ? color : category.getDefaultColor();
    }
}
