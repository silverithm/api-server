package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.Company;

/**
 * 공문 하단 발신부에 찍히는 기관 정보.
 *
 * 문서마다 달라지는 값(시행 문서번호·접수일자)은 결재 데이터에 이미 있으므로 여기 담지 않는다.
 * 여기 있는 값은 전부 기관 단위로 한 번 정해두는 값이다.
 */
public record DocumentFooterDTO(String postalCode,
                                String address,
                                String homepageUrl,
                                String phoneNumber,
                                String faxNumber,
                                String contactEmail,
                                String disclosureType) {

    public static DocumentFooterDTO from(Company company) {
        if (company == null) {
            return null;
        }
        return new DocumentFooterDTO(
                company.getPostalCode(),
                company.getAddressName(),
                company.getHomepageUrl(),
                company.getPhoneNumber(),
                company.getFaxNumber(),
                company.getContactEmail(),
                company.getDisclosureType());
    }
}
