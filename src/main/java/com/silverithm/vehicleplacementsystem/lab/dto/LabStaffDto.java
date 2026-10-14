package com.silverithm.vehicleplacementsystem.lab.dto;

/** 참석자 고르기용 직원 목록 한 줄 — 연락처·이메일 등은 담지 않는다. */
public final class LabStaffDto {

    private LabStaffDto() {
    }

    public record View(Long id, String name, String role, String position) {
    }
}
