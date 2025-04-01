package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
