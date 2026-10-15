package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCaseMeeting;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCaseMeetingRepository extends JpaRepository<LabCaseMeeting, Long> {

    List<LabCaseMeeting> findByCompanyIdAndHeldOnBetweenOrderByHeldOnDescIdDesc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabCaseMeeting> findByIdAndCompanyId(Long id, Long companyId);
}
