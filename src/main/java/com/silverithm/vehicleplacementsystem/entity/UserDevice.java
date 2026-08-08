package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림을 받는 기기 하나.
 *
 * <p>사용자 행에 토큰 컬럼 하나만 두면 새 기기가 이전 기기를 덮어써서, 폰과 태블릿을 같이 쓰는
 * 사람은 마지막에 앱을 켠 쪽에서만 알림을 받았다. 기기를 행으로 두어 전부에게 보낸다.
 *
 * <p>주인은 직원(Member) 또는 관리자 가입 계정(AppUser) 중 하나다. 계정 체계가 둘로 나뉘어 있어
 * 외래키 하나로 묶을 수 없으므로 컬럼을 나누고 둘 중 하나만 채운다.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_devices")
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "app_user_id")
    private Long appUserId;

    /** 같은 기기가 다른 계정으로 로그인하면 주인만 바뀌므로 토큰이 유일 키다 */
    @Column(name = "fcm_token", nullable = false, length = 500)
    private String fcmToken;

    @Column(name = "platform", length = 20)
    private String platform;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 마지막으로 토큰을 다시 올린 시각 — 오래 안 쓰는 기기를 골라낼 때 쓴다 */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** 같은 기기를 다른 계정이 쓰기 시작하면 주인을 갈아끼운다 */
    public void reassignTo(Long memberId, Long appUserId) {
        this.memberId = memberId;
        this.appUserId = appUserId;
        this.lastSeenAt = LocalDateTime.now();
    }
}
