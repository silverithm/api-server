package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.ExternalNotice;
import com.silverithm.vehicleplacementsystem.service.ExternalNoticeCrawlerService.LtcBoard;
import java.time.LocalDate;

public record ExternalNoticeDTO(
        Long id,
        String source,
        String sourceLabel,
        String title,
        String url,
        LocalDate postedDate
) {
    public static ExternalNoticeDTO from(ExternalNotice notice) {
        return new ExternalNoticeDTO(
                notice.getId(),
                notice.getSource(),
                LtcBoard.labelFor(notice.getSource()),
                notice.getTitle(),
                notice.getUrl(),
                notice.getPostedDate()
        );
    }
}
