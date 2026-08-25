package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회의 참석자. 서명은 결재선과 달리 순서 없이 각자 한다.
 *
 * <p>EXTERNAL은 계정이 없는 외부 참석자 — refId 없이 이름만 남고,
 * 서명은 관리자 화면에서 입회 서명(현장 그리기)으로 받는다.
 */
@Entity
@Table(name = "meeting_minutes_attendees")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesAttendee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_minutes_id", nullable = false)
    private MeetingMinutes meetingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendee_type", nullable = false, columnDefinition = "varchar(10)")
    private AttendeeType attendeeType;

    /** AppUser 또는 Member PK. EXTERNAL이면 null */
    @Column(name = "ref_id")
    private Long refId;

    /** 지정 시점 이름 스냅샷 */
    @Column(name = "attendee_name", nullable = false)
    private String attendeeName;

    @Column(name = "signature_url", length = 1000)
    private String signatureUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isSigned() {
        return signedAt != null;
    }

    public enum AttendeeType {
        ADMIN,    // AppUser (기관 관리자 계정)
        MEMBER,   // Member (직원)
        EXTERNAL  // 계정 없는 외부 참석자
    }
}
