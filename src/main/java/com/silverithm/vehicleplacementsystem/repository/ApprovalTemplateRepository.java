package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.ApprovalTemplate;
import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {

    List<ApprovalTemplate> findByCompanyOrderByCreatedAtDesc(Company company);

    List<ApprovalTemplate> findByCompanyAndIsActiveTrueOrderByCreatedAtDesc(Company company);

    List<ApprovalTemplate> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<ApprovalTemplate> findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(Long companyId);
}