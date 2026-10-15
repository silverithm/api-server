package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabFacilityLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabFacilityLogRepository extends JpaRepository<LabFacilityLog, Long> {

    /** 양 끝 날짜를 모두 포함한다 */
    List<LabFacilityLog> findByCompanyIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    List<LabFacilityLog> findByCompanyIdAndItemCodeOrderByDoneOnDescIdDesc(Long companyId, String itemCode);

    List<LabFacilityLog> findByCompanyIdAndItemCodeAndDoneOnBetweenOrderByDoneOnDescIdDesc(
            Long companyId, String itemCode, LocalDate from, LocalDate to);

    Optional<LabFacilityLog> findByIdAndCompanyId(Long id, Long companyId);
}
