package com.silverithm.vehicleplacementsystem.dto;

public record PasswordChangeRequest(String email, String currentPassword, String newPassword) {
}
