package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTemplateVersionRepository extends JpaRepository<ApprovalTemplateVersion, Long> {

    /** 버전 기록 화면 — 최근 것부터, 너무 길어지지 않게 100개까지 */
    List<ApprovalTemplateVersion> findTop100ByTemplateIdOrderByVersionNoDesc(Long templateId);

    Optional<ApprovalTemplateVersion> findFirstByTemplateIdOrderByVersionNoDesc(Long templateId);

    Optional<ApprovalTemplateVersion> findByIdAndTemplateId(Long id, Long templateId);
}
