package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VacationLimitRepository extends JpaRepository<VacationLimit, Long> {
    
    Optional<VacationLimit> findByDateAndRole(LocalDate date, VacationRequest.Role role);
    
    List<VacationLimit> findByDateBetween(LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT v FROM VacationLimit v WHERE v.date BETWEEN :startDate AND :endDate AND v.role = :role")
    List<VacationLimit> findByDateBetweenAndRole(
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate, 
            @Param("role") VacationRequest.Role role);
} 