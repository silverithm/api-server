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
    /** 프로필 사진 — 참가자 목록과 같은 자리에서 채운다. 없으면 앱이 이름 첫 글자로 그린다. */
    private String profileImageUrl;
    private LocalDateTime readAt;

    public static ChatMessageReaderDTO fromEntity(ChatMessageRead read) {
        return fromEntity(read, null);
    }

    /**
     * 사진까지 채워서 만든다.
     *
     * 사진은 읽음 기록에 없고 사람(Member/AppUser) 쪽에 있어서, 조회하는 서비스가
     * 찾아서 넣어 준다. 사진 없이 만들면 앱에서 이름 첫 글자만 나온다.
     */
    public static ChatMessageReaderDTO fromEntity(ChatMessageRead read, String profileImageUrl) {
        return ChatMessageReaderDTO.builder()
                .id(read.getId())
                .messageId(read.getMessage() != null ? read.getMessage().getId() : null)
                .userId(read.getUserId())
                .userName(read.getUserName())
                .profileImageUrl(profileImageUrl)
                .readAt(read.getReadAt())
                .build();
    }
}
