package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    
    List<Company> findByExposeTrue();
    
    @Query("SELECT c FROM Company c LEFT JOIN FETCH c.users WHERE c.expose = true")
    List<Company> findByExposeTrueWithUsers();
}
