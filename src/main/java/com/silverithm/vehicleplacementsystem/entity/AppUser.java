package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private UserRole userRole;

    private String refreshToken;
    private String companyName;
    private String companyAddressName;

    @Column(unique = true)
    private String customerKey;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Subscription subscription;

    private String billingKey;


    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "company_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "company_longitude"))
    })
    private Location companyAddress;

    public AppUser(String name, String email, String encode, UserRole role, String refreshToken,
                   String companyName, Location companyLocation, String companyAddressName, String customerKey) {
        this.username = name;
        this.email = email;
        this.password = encode;
        this.userRole = role;
        this.refreshToken = refreshToken;
        this.companyName = companyName;
        this.companyAddress = companyLocation;
        this.companyAddressName = companyAddressName;
        this.customerKey = customerKey;
    }

    public static AppUser of(UserDataDTO userDataDTO, String encodedPassowrd, TokenInfo tokenInfo,
                             Location companyLocation, String customerKey) {
        return new AppUser(
                userDataDTO.getName(),
                userDataDTO.getEmail(),
                encodedPassowrd,
                userDataDTO.getRole(),
                tokenInfo.getRefreshToken(),
                userDataDTO.getCompanyName(),
                companyLocation,
                userDataDTO.getCompanyAddress(),
                customerKey
        );
    }

    public void update(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public void updateCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void updateCompanyAddress(Location companyLocation, String companyAddressName) {
        this.companyAddress = companyLocation;
        this.companyAddressName = companyAddressName;
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
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
}
