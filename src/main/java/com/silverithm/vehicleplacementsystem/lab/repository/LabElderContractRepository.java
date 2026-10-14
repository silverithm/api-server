package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabElderContract;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabElderContractRepository extends JpaRepository<LabElderContract, Long> {

    Optional<LabElderContract> findByIdAndCompanyId(Long id, Long companyId);

    List<LabElderContract> findByCompanyIdOrderByContractStartDescIdDesc(Long companyId);

    List<LabElderContract> findByCompanyIdAndStatusOrderByContractStartDescIdDesc(Long companyId, String status);

    List<LabElderContract> findByCompanyIdAndElderlyIdOrderByContractStartDescIdDesc(Long companyId, Long elderlyId);

    List<LabElderContract> findByCompanyIdAndElderlyIdAndStatusOrderByContractStartDescIdDesc(Long companyId,
                                                                                            Long elderlyId,
                                                                                            String status);

    /** 같은 어르신의 다른 진행 중 계약이 있는지 — 자기 자신(id)은 빼고 본다 */
    boolean existsByElderlyIdAndStatusAndIdNot(Long elderlyId, String status, Long id);

    boolean existsByElderlyIdAndStatus(Long elderlyId, String status);
}
