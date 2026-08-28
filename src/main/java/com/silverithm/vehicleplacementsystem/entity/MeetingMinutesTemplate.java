package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 기관별 회의록 양식(섹션 구성 + AI 정리 지시). 회사당 여러 개를 만들 수 있고(V1.85),
 * 행이 하나도 없으면 애플리케이션 기본값을 쓴다 — schedule_category_settings(V1.78)와
 * 같은 널-폴백 방식이라 기관별 시드가 필요 없다.
 */
@Entity
@Table(name = "meeting_minutes_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** 양식 이름 (예: 전체회의용, 사례회의용) */
    @Column(nullable = false)
    @Builder.Default
    private String name = "기본 양식";

    /** [{"key","label"}] 섹션 구성 */
    @Column(nullable = false, columnDefinition = "JSON")
    private String sections;

    /** AI 자동 정리가 따라갈 추가 지시 — 말투·관점 등 */
    @Column(name = "ai_instruction", columnDefinition = "TEXT")
    private String aiInstruction;

    /** AI 자동 정리가 따라갈 출력 형식 예시 (few-shot) */
    @Column(name = "format_example", columnDefinition = "TEXT")
    private String formatExample;

    /** 회의록 작성 시 기본으로 선택되는 양식 — 회사당 최대 1개만 true여야 한다(서비스 계층에서 보장) */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
}
