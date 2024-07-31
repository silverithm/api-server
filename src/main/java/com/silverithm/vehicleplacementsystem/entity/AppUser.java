package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.Location;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private UserRole userRole;

    private String accessToken;
    private String refreshToken;
    private String companyName;
    private String companyAddressName;


    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "company_latitude")),
            @AttributeOverride(name = "longitude", column = @Column(name = "company_longitude"))
    })
    private Location companyAddress;

    public AppUser(String name, String email, String encode, UserRole role, String accessToken, String refreshToken,
                   String companyName, Location companyLocation, String companyAddressName) {
        this.username = name;
        this.email = email;
        this.password = encode;
        this.userRole = role;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.companyName = companyName;
        this.companyAddress = companyLocation;
        this.companyAddressName = companyAddressName;
    }

    public void update(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void updateRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

}
