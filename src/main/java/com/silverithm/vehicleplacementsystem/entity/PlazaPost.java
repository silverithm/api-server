package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 케어브이 광장 게시글 — 전 기관 공유(cross-company) 리소스.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "plaza_posts")
public class PlazaPost extends BaseEntity {

    public enum Board {
        QNA("qna"), REVIEW("review"), FREE("free");

        private final String key;

        Board(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static Board fromKey(String key) {
            for (Board b : values()) {
                if (b.key.equalsIgnoreCase(key)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unknown board: " + key);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Board board;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String authorId;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Column(length = 100)
    private String companyName;

    @Column(nullable = false)
    private boolean isAnonymous;

    @Column(nullable = false)
    private boolean isPinned;

    @Column(nullable = false)
    private boolean isHidden;

    /** 광장 운영자가 관리자 모드로 작성한 [운영] 공지 */
    @Column(nullable = false)
    private boolean isOfficial;

    @Column(nullable = false)
    private int viewCount;
}
