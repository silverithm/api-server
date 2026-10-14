package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabStaffCert;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabStaffCertRepository extends JpaRepository<LabStaffCert, Long> {

    List<LabStaffCert> findByCompanyIdAndMemberIdOrderByDoneOnDescIdDesc(Long companyId, Long memberId);

    List<LabStaffCert> findByCompanyIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(Long companyId, LocalDate from,
                                                                              LocalDate to);

    List<LabStaffCert> findByCompanyIdAndMemberIdAndDoneOnBetweenOrderByDoneOnDescIdDesc(Long companyId,
                                                                                         Long memberId,
                                                                                         LocalDate from,
                                                                                         LocalDate to);

    Optional<LabStaffCert> findByIdAndCompanyId(Long id, Long companyId);
}
