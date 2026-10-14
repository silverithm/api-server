package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCareEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCareEventRepository extends JpaRepository<LabCareEvent, Long> {

    List<LabCareEvent> findByCompanyIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
            Long companyId, LocalDateTime from, LocalDateTime toExclusive);

    List<LabCareEvent> findByCompanyIdAndElderlyIdOrderByOccurredAtDescIdDesc(Long companyId, Long elderlyId);

    List<LabCareEvent> findByCompanyIdAndStatusOrderByOccurredAtDescIdDesc(Long companyId, String status);

    Optional<LabCareEvent> findByIdAndCompanyId(Long id, Long companyId);
}
