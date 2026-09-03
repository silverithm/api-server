package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.NoticeReader;
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
public class NoticeReaderDTO {
    private Long id;
    private Long noticeId;
    private String userId;
    private String userName;
    /** 프로필 사진 — 읽음 기록엔 없고 사람 쪽에 있어서 조회하는 서비스가 채워 준다 */
    private String profileImageUrl;
    private LocalDateTime readAt;

    public static NoticeReaderDTO fromEntity(NoticeReader reader) {
        return fromEntity(reader, null);
    }

    public static NoticeReaderDTO fromEntity(NoticeReader reader, String profileImageUrl) {
        return NoticeReaderDTO.builder()
                .id(reader.getId())
                .noticeId(reader.getNotice().getId())
                .userId(reader.getUserId())
                .userName(reader.getUserName())
                .profileImageUrl(profileImageUrl)
                .readAt(reader.getReadAt())
                .build();
    }
}