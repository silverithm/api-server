package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabGuideDelivery;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabGuideDeliveryRepository extends JpaRepository<LabGuideDelivery, Long> {

    List<LabGuideDelivery> findByCompanyIdAndKindOrderByDeliveredOnDescIdDesc(Long companyId, String kind);

    List<LabGuideDelivery> findByCompanyIdAndKindAndElderlyIdOrderByDeliveredOnDescIdDesc(
            Long companyId, String kind, Long elderlyId);

    Optional<LabGuideDelivery> findByIdAndCompanyId(Long id, Long companyId);
}
