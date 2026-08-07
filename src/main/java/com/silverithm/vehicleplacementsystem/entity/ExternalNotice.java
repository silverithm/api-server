package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 외부 기관(노인장기요양보험(longtermcare.or.kr) 등)에서 자동 수집한 공지사항.
 * source + external_id 조합으로 중복 수집을 막는다.
 */
@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "external_notice", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}))
public class ExternalNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수집 출처 (게시판 코드, 예: LTC_NOTICE = 노인장기요양보험 공지사항 게시판) */
    @Column(nullable = false, length = 30)
    private String source;

    /** 출처 내 원본 게시글 ID (예: boardId) */
    @Column(name = "external_id", nullable = false, length = 30)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 700)
    private String url;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public ExternalNotice(String source, String externalId, String title, String url, LocalDate postedDate) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.url = url;
        this.postedDate = postedDate;
    }
}
