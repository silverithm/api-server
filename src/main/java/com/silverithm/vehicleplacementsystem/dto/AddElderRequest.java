package com.silverithm.vehicleplacementsystem.dto;

public record AddElderRequest(String name, int age, String homeAddress, boolean requireFrontSeat) {
}
