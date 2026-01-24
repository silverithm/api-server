package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatMessageRead;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageReaderDTO {

    private Long id;
    private Long messageId;
    private String userId;
    private String userName;
    private LocalDateTime readAt;

    public static ChatMessageReaderDTO fromEntity(ChatMessageRead read) {
        return ChatMessageReaderDTO.builder()
                .id(read.getId())
                .messageId(read.getMessage() != null ? read.getMessage().getId() : null)
                .userId(read.getUserId())
                .userName(read.getUserName())
                .readAt(read.getReadAt())
                .build();
    }
}
