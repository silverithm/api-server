package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Notice;
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
public class NoticeDTO {
    private Long id;
    private String title;
    private String content;
    private String priority;
    private String status;
    private Boolean isPinned;
    private String authorId;
    private String authorName;
    private Long companyId;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    public static NoticeDTO fromEntity(Notice notice) {
        return NoticeDTO.builder()
                .id(notice.getId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .priority(notice.getPriority().name())
                .status(notice.getStatus().name())
                .isPinned(notice.getIsPinned())
                .authorId(notice.getAuthorId())
                .authorName(notice.getAuthorName())
                .companyId(notice.getCompany().getId())
                .viewCount(notice.getViewCount())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .publishedAt(notice.getPublishedAt())
                .build();
    }
}