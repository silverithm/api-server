package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.NoticeReader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NoticeReaderRepository extends JpaRepository<NoticeReader, Long> {

    List<NoticeReader> findByNoticeIdOrderByReadAtDesc(Long noticeId);

    Optional<NoticeReader> findByNoticeIdAndUserId(Long noticeId, String userId);

    boolean existsByNoticeIdAndUserId(Long noticeId, String userId);

    long countByNoticeId(Long noticeId);

    void deleteByNoticeId(Long noticeId);

    List<NoticeReader> findByUserId(String userId);
}