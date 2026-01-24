package com.silverithm.vehicleplacementsystem.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomUpdateRequest {

    private String name;
    private String description;
    private String thumbnailUrl;
}
