package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.NoticeComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    List<NoticeComment> findByNoticeIdOrderByCreatedAtAsc(Long noticeId);

    List<NoticeComment> findByNoticeIdOrderByCreatedAtDesc(Long noticeId);

    long countByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);
}