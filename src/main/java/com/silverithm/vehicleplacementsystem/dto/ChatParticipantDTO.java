package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatParticipantDTO {

    private Long id;
    private Long chatRoomId;
    private String userId;
    private String userName;
    private String role;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
    private Long lastReadMessageId;
    private Boolean isActive;
    private LocalDateTime leftAt;
    private String leaveReason;

    public static ChatParticipantDTO fromEntity(ChatParticipant participant) {
        return ChatParticipantDTO.builder()
                .id(participant.getId())
                .chatRoomId(participant.getChatRoom() != null ? participant.getChatRoom().getId() : null)
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .lastReadAt(participant.getLastReadAt())
                .lastReadMessageId(participant.getLastReadMessageId())
                .isActive(participant.getIsActive())
                .leftAt(participant.getLeftAt())
                .leaveReason(participant.getLeaveReason() != null ? participant.getLeaveReason().name() : null)
                .build();
    }
}
