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
    private String position;
    private String role;
    private String memberRole;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
    private Long lastReadMessageId;
    private Boolean isActive;
    private LocalDateTime leftAt;
    private String leaveReason;

    public static ChatParticipantDTO fromEntity(ChatParticipant participant) {
        return fromEntity(participant, null, null);
    }

    public static ChatParticipantDTO fromEntity(ChatParticipant participant, String position) {
        return fromEntity(participant, position, null);
    }

    public static ChatParticipantDTO fromEntity(ChatParticipant participant, String position, String memberRole) {
        return ChatParticipantDTO.builder()
                .id(participant.getId())
                .chatRoomId(participant.getChatRoom() != null ? participant.getChatRoom().getId() : null)
                .userId(participant.getUserId())
                .userName(participant.getUserName())
                .position(position)
                .role(participant.getRole().name())
                .memberRole(memberRole)
                .joinedAt(participant.getJoinedAt())
                .lastReadAt(participant.getLastReadAt())
                .lastReadMessageId(participant.getLastReadMessageId())
                .isActive(participant.getIsActive())
                .leftAt(participant.getLeftAt())
                .leaveReason(participant.getLeaveReason() != null ? participant.getLeaveReason().name() : null)
                .build();
    }
}
