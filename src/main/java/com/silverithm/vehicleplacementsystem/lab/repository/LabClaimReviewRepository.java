package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabClaimReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabClaimReviewRepository extends JpaRepository<LabClaimReview, Long> {

    Optional<LabClaimReview> findByIdAndCompanyId(Long id, Long companyId);

    Optional<LabClaimReview> findByElderlyIdAndClaimMonth(Long elderlyId, String claimMonth);

    List<LabClaimReview> findByCompanyIdAndClaimMonthOrderByElderlyIdAscIdAsc(Long companyId, String claimMonth);
}
