package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabTransportLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTransportLogRepository extends JpaRepository<LabTransportLog, Long> {

    List<LabTransportLog> findByCompanyIdAndRunDateBetweenOrderByRunDateAscVehicleNoAscIdAsc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabTransportLog> findByCompanyIdAndRunDateAndDirectionAndVehicleNo(
            Long companyId, LocalDate runDate, String direction, String vehicleNo);

    Optional<LabTransportLog> findByIdAndCompanyId(Long id, Long companyId);
}
