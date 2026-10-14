package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBathLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBathLogRepository extends JpaRepository<LabBathLog, Long> {

    Optional<LabBathLog> findByIdAndCompanyId(Long id, Long companyId);

    List<LabBathLog> findByCompanyIdAndBathDateBetweenOrderByBathDateAscStartTimeAscIdAsc(Long companyId,
                                                                                         LocalDate from,
                                                                                         LocalDate to);

    List<LabBathLog> findByCompanyIdAndElderlyIdAndBathDateBetweenOrderByBathDateAscStartTimeAscIdAsc(Long companyId,
                                                                                                    Long elderlyId,
                                                                                                    LocalDate from,
                                                                                                    LocalDate to);
}
