package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Company;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyListDTO {
    
    private Long id;
    private String name;
    private String addressName;
    private Location companyAddress;
    private List<String> userEmails;
    
    public static CompanyListDTO fromEntity(Company company) {
        List<String> emails = company.getUsers() != null 
            ? company.getUsers().stream()
                .map(user -> user.getEmail())
                .collect(java.util.stream.Collectors.toList())
            : java.util.Collections.emptyList();
            
        return CompanyListDTO.builder()
                .id(company.getId())
                .name(company.getName())
                .addressName(company.getAddressName())
                .companyAddress(company.getCompanyAddress())
                .userEmails(emails)
                .build();
    }
} 