package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabIntakeInquiry;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabIntakeInquiryRepository extends JpaRepository<LabIntakeInquiry, Long> {

    List<LabIntakeInquiry> findByCompanyIdOrderByInquiryDateDescIdDesc(Long companyId);

    List<LabIntakeInquiry> findByCompanyIdAndStatusOrderByInquiryDateDescIdDesc(Long companyId, String status);

    Optional<LabIntakeInquiry> findByIdAndCompanyId(Long id, Long companyId);
}
