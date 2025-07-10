package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AppVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String iosVersion;

    @Column(nullable = false)
    private String iosMinimumVersion;

    @Column(nullable = false)
    private String androidVersion;

    @Column(nullable = false)
    private String androidMinimumVersion;

    @Column(columnDefinition = "TEXT")
    private String updateMessage;

    @Column(nullable = false)
    private boolean forceUpdate;

    public void updateVersionInfo(String iosVersion, String iosMinimumVersion,
                                  String androidVersion, String androidMinimumVersion,
                                  String updateMessage, boolean forceUpdate) {
        this.iosVersion = iosVersion;
        this.iosMinimumVersion = iosMinimumVersion;
        this.androidVersion = androidVersion;
        this.androidMinimumVersion = androidMinimumVersion;
        this.updateMessage = updateMessage;
        this.forceUpdate = forceUpdate;
    }
}