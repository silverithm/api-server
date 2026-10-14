package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCareDelivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCareDeliveryRepository extends JpaRepository<LabCareDelivery, Long> {

    Optional<LabCareDelivery> findByElderlyIdAndRecordMonth(Long elderlyId, String recordMonth);

    List<LabCareDelivery> findByCompanyIdAndRecordMonth(Long companyId, String recordMonth);

    Optional<LabCareDelivery> findByIdAndCompanyId(Long id, Long companyId);
}
