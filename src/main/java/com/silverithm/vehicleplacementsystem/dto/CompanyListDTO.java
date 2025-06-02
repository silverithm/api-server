package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Company;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyListDTO {
    
    private Long id;
    private String name;
    private String addressName;
    private Location companyAddress;
    
    public static CompanyListDTO fromEntity(Company company) {
        return CompanyListDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .addressName(company.getAddressName())
                .companyAddress(company.getCompanyAddress())
                .build();
    }
} 