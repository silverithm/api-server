package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMeeting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCommitteeMeetingRepository extends JpaRepository<LabCommitteeMeeting, Long> {

    List<LabCommitteeMeeting> findByCompanyIdAndHeldOnBetweenOrderByHeldOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabCommitteeMeeting> findByIdAndCompanyId(Long id, Long companyId);
}
