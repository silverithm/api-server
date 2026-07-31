package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.DocumentNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentNumberCounterRepository extends JpaRepository<DocumentNumberCounter, Long> {

    // 행 존재 보장 (동시 첫 발급 경쟁에도 안전)
    @Modifying
    @Query(value = "INSERT IGNORE INTO document_number_counters (company_id, year, seq) VALUES (:companyId, :year, 0)",
            nativeQuery = true)
    void ensureCounter(@Param("companyId") Long companyId, @Param("year") Integer year);

    // 트랜잭션 내에서 잠그고 증가시킨다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentNumberCounter> findByCompanyIdAndYear(Long companyId, Integer year);
}
