package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.CompanyLibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyLibraryItemRepository extends JpaRepository<CompanyLibraryItem, Long> {

    List<CompanyLibraryItem> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
