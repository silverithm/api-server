package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBankStatement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabBankStatementRepository extends JpaRepository<LabBankStatement, Long> {

    List<LabBankStatement> findByCompanyIdAndStatementMonthOrderByAccountIdAsc(Long companyId, String month);

    List<LabBankStatement> findByCompanyIdAndAccountIdOrderByStatementMonthAsc(Long companyId, Long accountId);

    List<LabBankStatement> findByCompanyIdAndAccountIdAndStatementMonthOrderByIdAsc(Long companyId, Long accountId,
                                                                                    String month);

    Optional<LabBankStatement> findByAccountIdAndStatementMonth(Long accountId, String month);

    Optional<LabBankStatement> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByAccountId(Long accountId);
}
