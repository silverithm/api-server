package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabBankAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabBankAccountRepository extends JpaRepository<LabBankAccount, Long> {

    List<LabBankAccount> findByCompanyIdOrderByActiveDescIdAsc(Long companyId);

    Optional<LabBankAccount> findByIdAndCompanyId(Long id, Long companyId);

    /** 월말 잔액 저장·통장 삭제가 같은 통장 행을 잠가 "잔액 기록이 없다고 보고 지우는" 순간을 막는다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LabBankAccount a WHERE a.id = :id AND a.companyId = :companyId")
    Optional<LabBankAccount> lockByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
