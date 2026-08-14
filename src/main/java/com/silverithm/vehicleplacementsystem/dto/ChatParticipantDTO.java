package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatParticipant;
import com.silverithm.vehicleplacementsystem.entity.ChatPersonRef;
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
    /** 예전 문자열 표기. 앱이 새 필드로 옮겨가면 뺀다 */
    @Deprecated
    private String userId;
    /** ADMIN | MEMBER */
    private String userType;
    /** 원시 id (app_user.id 또는 members.id) */
    private Long userRefId;
    private String userName;
    private String position;
    private String role;
    private String memberRole;
    private String profileImageUrl;
    private LocalDateTime joinedAt;
    private LocalDateTime lastReadAt;
    private Long lastReadMessageId;
    private Boolean isActive;
    private LocalDateTime leftAt;
    private String leaveReason;

    public static ChatParticipantDTO fromEntity(ChatParticipant participant) {
        return fromEntity(participant, null, null, null);
    }

    public static ChatParticipantDTO fromEntity(ChatParticipant participant, String position) {
        return fromEntity(participant, position, null, null);
    }

    public static ChatParticipantDTO fromEntity(ChatParticipant participant, String position, String memberRole) {
        return fromEntity(participant, position, memberRole, null);
    }

    public static ChatParticipantDTO fromEntity(ChatParticipant participant, String position, String memberRole,
                                                  String profileImageUrl) {
        return ChatParticipantDTO.builder()
                .id(participant.getId())
                .chatRoomId(participant.getChatRoom() != null ? participant.getChatRoom().getId() : null)
                .userId(person(participant).legacyId() != null
                        ? person(participant).legacyId() : participant.getUserId())
                .userType(person(participant).type())
                .userRefId(person(participant).refId())
                .userName(participant.getUserName())
                .position(position)
                .role(participant.getRole().name())
                .memberRole(memberRole)
                .profileImageUrl(profileImageUrl)
                .joinedAt(participant.getJoinedAt())
                .lastReadAt(participant.getLastReadAt())
                .lastReadMessageId(participant.getLastReadMessageId())
                .isActive(participant.getIsActive())
                .leftAt(participant.getLeftAt())
                .leaveReason(participant.getLeaveReason() != null ? participant.getLeaveReason().name() : null)
                .build();
    }

    /** 문자열이 아니라 참조 칼럼에서 사람을 읽는다 */
    private static ChatPersonRef person(ChatParticipant participant) {
        return ChatPersonRef.of(participant.getMemberId(), participant.getAppUserId());
    }
}
