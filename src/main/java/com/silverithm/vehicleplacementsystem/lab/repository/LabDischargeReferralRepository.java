package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabDischargeReferral;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabDischargeReferralRepository extends JpaRepository<LabDischargeReferral, Long> {

    Optional<LabDischargeReferral> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByElderlyIdAndDischargeOnAndIdNot(Long elderlyId, LocalDate dischargeOn, Long id);

    boolean existsByElderlyIdAndDischargeOn(Long elderlyId, LocalDate dischargeOn);

    List<LabDischargeReferral> findByCompanyIdOrderByDischargeOnDescIdDesc(Long companyId);

    List<LabDischargeReferral> findByCompanyIdAndDischargeOnBetweenOrderByDischargeOnDescIdDesc(Long companyId,
                                                                                              LocalDate from,
                                                                                              LocalDate to);

    List<LabDischargeReferral> findByCompanyIdAndElderlyIdOrderByDischargeOnDescIdDesc(Long companyId,
                                                                                     Long elderlyId);

    List<LabDischargeReferral> findByCompanyIdAndElderlyIdAndDischargeOnBetweenOrderByDischargeOnDescIdDesc(
            Long companyId, Long elderlyId, LocalDate from, LocalDate to);
}
