package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabElderDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabElderDocumentRepository extends JpaRepository<LabElderDocument, Long> {

    List<LabElderDocument> findByCompanyIdOrderByElderlyIdAscDocTypeAscIdAsc(Long companyId);

    List<LabElderDocument> findByCompanyIdAndElderlyIdOrderByDocTypeAscIdAsc(Long companyId, Long elderlyId);

    Optional<LabElderDocument> findByIdAndCompanyId(Long id, Long companyId);
}
