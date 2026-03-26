package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    
    List<Company> findByExposeTrue();

    Optional<Company> findByCompanyCodeIgnoreCase(String companyCode);

    boolean existsByCompanyCode(String companyCode);
    
    @Query("SELECT c FROM Company c LEFT JOIN FETCH c.users WHERE c.expose = true")
    List<Company> findByExposeTrueWithUsers();
}
