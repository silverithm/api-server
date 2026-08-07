package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ExternalNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalNoticeRepository extends JpaRepository<ExternalNotice, Long> {

    boolean existsBySourceAndExternalId(String source, String externalId);

    Page<ExternalNotice> findAllByOrderByPostedDateDesc(Pageable pageable);

    Page<ExternalNotice> findAllBySourceOrderByPostedDateDesc(String source, Pageable pageable);
}
