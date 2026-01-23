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
    private LocalDateTime readAt;

    public static NoticeReaderDTO fromEntity(NoticeReader reader) {
        return NoticeReaderDTO.builder()
                .id(reader.getId())
                .noticeId(reader.getNotice().getId())
                .userId(reader.getUserId())
                .userName(reader.getUserName())
                .readAt(reader.getReadAt())
                .build();
    }
}