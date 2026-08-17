package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomDTO {

    private Long id;
    private String name;
    private String description;
    private Long companyId;
    private String createdBy;
    private String createdByName;
    private String thumbnailUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private int participantCount;
    private ChatMessageDTO lastMessage;
    private int unreadCount;
    private List<ChatParticipantDTO> participants;

    /** 방 상단 고정 공지 (없으면 전부 null) */
    private Long noticeMessageId;
    private String noticeContent;
    private String noticeByName;
    private LocalDateTime noticeAt;
    private String noticeFileName;
    private String noticeFileUrl;

    public static ChatRoomDTO fromEntity(ChatRoom room) {
        return ChatRoomDTO.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .companyId(room.getCompany() != null ? room.getCompany().getId() : null)
                .createdBy(room.getCreatedBy())
                .createdByName(room.getCreatedByName())
                .thumbnailUrl(room.getThumbnailUrl())
                .status(room.getStatus().name())
                .createdAt(room.getCreatedAt())
                .lastMessageAt(room.getLastMessageAt())
                .participantCount(room.getParticipants() != null ?
                        (int) room.getParticipants().stream().filter(p -> p.getIsActive()).count() : 0)
                .noticeMessageId(room.getNoticeMessageId())
                .noticeContent(room.getNoticeContent())
                .noticeByName(room.getNoticeByName())
                .noticeAt(room.getNoticeAt())
                .noticeFileName(room.getNoticeFileName())
                .noticeFileUrl(room.getNoticeFileUrl())
                .build();
    }

    public static ChatRoomDTO fromEntityWithParticipants(ChatRoom room) {
        ChatRoomDTO dto = fromEntity(room);
        if (room.getParticipants() != null) {
            dto.setParticipants(room.getParticipants().stream()
                    .filter(p -> p.getIsActive())
                    .map(ChatParticipantDTO::fromEntity)
                    .collect(Collectors.toList()));
        }
        return dto;
    }
}
