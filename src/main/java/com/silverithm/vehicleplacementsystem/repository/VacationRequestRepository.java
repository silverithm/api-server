package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VacationRequestRepository extends JpaRepository<VacationRequest, Long> {
    
    List<VacationRequest> findByDateBetween(LocalDate startDate, LocalDate endDate);
    
    List<VacationRequest> findByDate(LocalDate date);
    
    @Query("SELECT v FROM VacationRequest v WHERE v.date = :date AND v.role = :role")
    List<VacationRequest> findByDateAndRole(@Param("date") LocalDate date, @Param("role") VacationRequest.Role role);
    
    @Query("SELECT v FROM VacationRequest v WHERE v.date BETWEEN :startDate AND :endDate AND v.role = :role")
    List<VacationRequest> findByDateBetweenAndRole(
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate, 
            @Param("role") VacationRequest.Role role);
    
    @Query("SELECT v FROM VacationRequest v WHERE v.userName = :userName AND v.date BETWEEN :startDate AND :endDate")
    List<VacationRequest> findByUserNameAndDateBetween(
            @Param("userName") String userName,
            @Param("startDate") LocalDate startDate, 
            @Param("endDate") LocalDate endDate);
} 