package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.entity.Company;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 기관 행 잠금 — 유일 키로 막을 수 없는 "같은 종류는 하나" 확인이 동시에 겹치지 않게 기관 단위로 줄 세운다. */
public interface LabCompanyLockRepository extends Repository<Company, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Company c WHERE c.id = :id")
    Optional<Company> lockById(@Param("id") Long id);
}
