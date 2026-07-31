package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 광장 게시글 댓글 (parentId로 대댓글 1단계 지원, Q&A 채택 포함).
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "plaza_comments")
public class PlazaComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    private Long parentId;

    @Column(nullable = false)
    private String authorId;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Column(length = 100)
    private String companyName;

    @Column(nullable = false)
    private boolean isAnonymous;

    @Column(nullable = false)
    private boolean isAccepted;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
