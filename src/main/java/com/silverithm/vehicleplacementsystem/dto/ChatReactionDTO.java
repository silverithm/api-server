package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ChatMessageReaction;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReactionDTO {

    private Long id;
    private Long messageId;
    private String userId;
    private String userName;
    private String emoji;
    private LocalDateTime createdAt;

    public static ChatReactionDTO fromEntity(ChatMessageReaction reaction) {
        return ChatReactionDTO.builder()
                .id(reaction.getId())
                .messageId(reaction.getMessage().getId())
                .userId(reaction.getUserId())
                .userName(reaction.getUserName())
                .emoji(reaction.getEmoji())
                .createdAt(reaction.getCreatedAt())
                .build();
    }

    /**
     * 이모지별 그룹화된 리액션 요약
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReactionSummary {
        private String emoji;
        private int count;
        private List<String> userNames;
        private boolean myReaction; // 현재 사용자가 이 이모지를 남겼는지
    }
}
