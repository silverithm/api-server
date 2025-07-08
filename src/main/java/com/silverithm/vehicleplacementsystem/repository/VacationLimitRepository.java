package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.VacationLimit;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.entity.Company;
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

    Optional<VacationLimit> findByCompanyAndDateAndRole(Company company, LocalDate date, VacationRequest.Role role);

    List<VacationLimit> findByCompanyAndDateInAndRoleIn(Company company,
                                                        List<LocalDate> dates,
                                                        List<VacationRequest.Role> roles);

    List<VacationLimit> findByCompanyAndDateBetween(Company company, LocalDate startDate, LocalDate endDate);

    @Query("SELECT v FROM VacationLimit v WHERE v.company = :company AND v.date BETWEEN :startDate AND :endDate AND v.role = :role")
    List<VacationLimit> findByCompanyAndDateBetweenAndRole(
            @Param("company") Company company,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("role") VacationRequest.Role role);

    @Query("SELECT v FROM VacationLimit v WHERE v.company = :company ORDER BY v.date ASC")
    List<VacationLimit> findByCompanyOrderByDateAsc(@Param("company") Company company);

    @Query("SELECT v FROM VacationLimit v WHERE v.date BETWEEN :startDate AND :endDate AND v.role = :role")
    List<VacationLimit> findByDateBetweenAndRole(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("role") VacationRequest.Role role);
} 