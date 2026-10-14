package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabRiskAssessment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabRiskAssessmentRepository extends JpaRepository<LabRiskAssessment, Long> {

    List<LabRiskAssessment> findByCompanyIdAndElderlyIdOrderByAssessedOnDescIdDesc(Long companyId, Long elderlyId);

    Optional<LabRiskAssessment> findByIdAndCompanyId(Long id, Long companyId);

    /**
     * 어르신·종류별로 가장 최근 평가일의 평가들. 이력을 전부 읽지 않고 최신 날짜 행만 가져온다.
     * 같은 날 두 번 평가한 경우 여러 행이 올 수 있어 호출자가 id가 큰 쪽을 고른다.
     */
    @Query("SELECT a FROM LabRiskAssessment a WHERE a.companyId = :companyId AND a.assessedOn = ("
            + "SELECT MAX(b.assessedOn) FROM LabRiskAssessment b "
            + "WHERE b.companyId = :companyId AND b.elderlyId = a.elderlyId AND b.kind = a.kind)")
    List<LabRiskAssessment> findLatestPerElderAndKind(@Param("companyId") Long companyId);
}
