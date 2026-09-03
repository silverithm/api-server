package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.NoticeComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeCommentDTO {
    private Long id;
    private Long noticeId;
    private String authorId;
    private String authorName;
    /** 프로필 사진 — 댓글엔 없고 사람 쪽에 있어서 조회하는 서비스가 채워 준다 */
    private String profileImageUrl;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeCommentDTO fromEntity(NoticeComment comment) {
        return fromEntity(comment, null);
    }

    public static NoticeCommentDTO fromEntity(NoticeComment comment, String profileImageUrl) {
        return NoticeCommentDTO.builder()
                .profileImageUrl(profileImageUrl)
                .id(comment.getId())
                .noticeId(comment.getNotice().getId())
                .authorId(comment.getAuthorId())
                .authorName(comment.getAuthorName())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}