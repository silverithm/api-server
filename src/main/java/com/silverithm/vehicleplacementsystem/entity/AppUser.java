package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Table(indexes = {
        @Index(name = "idx_app_user_email", columnList = "email"),
})
@Getter
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    /**
     * 직책 표시명 (시설장·사무국장 등). 직원(Member)과 같은 방식으로 둔다.
     * 비어 있으면 화면에서는 '관리자'로 보인다.
     */
    @Column
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position positionEntity;

    @Column(nullable = false)
    private String email;

    private String password;

    private UserRole userRole;

    private String refreshToken;

    @Column(name = "fcm_token")
    private String fcmToken;

    /** 푸시 알림 수신 여부. 기본 true — 앱 설정에서 끄면 false. */
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    public void updatePushEnabled(boolean enabled) {
        this.pushEnabled = enabled;
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;


    @Column(unique = true)
    private String customerKey;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Subscription subscription;

    private String billingKey;

    private LocalDateTime deletedAt;

    @Column(name = "signature_url", length = 1000)
    private String signatureUrl;

    /** 관리자 프로필 사진. 직원(members.profile_image_url)과 같은 규격의 절대 URL */
    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;



    public AppUser(String name, String email, String encode, UserRole role, String refreshToken,
                   Company company, String customerKey) {
        this.username = name;
        this.email = email;
        this.password = encode;
        this.userRole = role;
        this.refreshToken = refreshToken;
        this.company = company;
        this.customerKey = customerKey;
    }

    public static AppUser of(UserDataDTO userDataDTO, String encodedPassowrd, TokenInfo tokenInfo,
                             Company company, String customerKey) {
        return new AppUser(
                userDataDTO.getName(),
                userDataDTO.getEmail(),
                encodedPassowrd,
                userDataDTO.getRole(),
                tokenInfo.getRefreshToken(),
                company,
                customerKey
        );
    }

    public void update(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void addCompany(Company company) {
        this.company = company;
        if (company.getUsers() != null && !company.getUsers().contains(this)) {
            company.getUsers().add(this);
        }
    }

    public void updateCompanyName(String companyName) {
        this.company.updateName(companyName);
    }

    public void updateCompanyAddress(Location companyLocation, String companyAddressName) {
        this.company.updateAddress(companyAddressName, companyLocation);
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateSignature(String signatureUrl) {
        this.signatureUrl = signatureUrl;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 직책 변경. 직원(Member)과 같이 FK와 표시명을 함께 들고 있는다 —
     * 표시할 때 지연 로딩을 타지 않도록 이름을 스냅샷으로 남긴다.
     * null을 주면 직책 없음(화면에는 '관리자')으로 돌아간다.
     */
    public void updatePosition(Position position) {
        this.positionEntity = position;
        this.position = position != null ? position.getName() : null;
    }


    public boolean isActiveSubscription() {
        if (this.subscription != null && this.subscription.getStatus().equals(SubscriptionStatus.ACTIVE)) {
            return true;
        }
        return false;
    }

    public void updateBillingKey(String billingKey) {
        this.billingKey = billingKey;
    }

    public boolean isEmptyBillingKey() {
        return this.billingKey == null || this.billingKey.isEmpty();
    }

    public void updateCustomerKey(String customerKey) {
        this.customerKey = customerKey;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
