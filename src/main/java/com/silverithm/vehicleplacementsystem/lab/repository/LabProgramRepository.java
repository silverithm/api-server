package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabProgram;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabProgramRepository extends JpaRepository<LabProgram, Long> {

    /** 양 끝 날짜를 모두 포함한다 — 월말 하루가 빠지지 않게 호출자가 말일을 넘긴다 */
    List<LabProgram> findByCompanyIdAndProgramDateBetweenOrderByProgramDateAscStartTimeAscIdAsc(
            Long companyId, LocalDate from, LocalDate to);

    Optional<LabProgram> findByIdAndCompanyId(Long id, Long companyId);
}
