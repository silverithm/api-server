package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.AppVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersionResponseDTO {
    private String iosVersion;
    private String iosMinimumVersion;
    private String androidVersion;
    private String androidMinimumVersion;
    private String updateMessage;
    private boolean forceUpdate;

    public static AppVersionResponseDTO from(AppVersion appVersion) {
        return AppVersionResponseDTO.builder()
                .iosVersion(appVersion.getIosVersion())
                .iosMinimumVersion(appVersion.getIosMinimumVersion())
                .androidVersion(appVersion.getAndroidVersion())
                .androidMinimumVersion(appVersion.getAndroidMinimumVersion())
                .updateMessage(appVersion.getUpdateMessage())
                .forceUpdate(appVersion.isForceUpdate())
                .build();
    }
}