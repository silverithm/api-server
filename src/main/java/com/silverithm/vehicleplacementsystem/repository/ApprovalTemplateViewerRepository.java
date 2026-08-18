package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplateViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalTemplateViewerRepository extends JpaRepository<ApprovalTemplateViewer, Long> {

    List<ApprovalTemplateViewer> findByTemplateId(Long templateId);

    void deleteByTemplateId(Long templateId);
}
