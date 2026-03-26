package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompanyCodeService {

    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final CompanyRepository companyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateUniqueCode() {
        String companyCode;
        do {
            companyCode = generateCode();
        } while (companyRepository.existsByCompanyCode(companyCode));

        return companyCode;
    }

    public String normalize(String companyCode) {
        if (companyCode == null) {
            return "";
        }

        return companyCode.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(CODE_CHARACTERS.length());
            builder.append(CODE_CHARACTERS.charAt(index));
        }

        return builder.toString();
    }
}
