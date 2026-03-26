package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class MemberRoleConverter implements AttributeConverter<Member.Role, String> {

    @Override
    public String convertToDatabaseColumn(Member.Role attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public Member.Role convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        return Member.Role.valueOf(dbData.trim().toUpperCase());
    }
}
