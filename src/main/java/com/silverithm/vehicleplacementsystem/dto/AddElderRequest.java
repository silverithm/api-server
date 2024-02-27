package com.silverithm.vehicleplacementsystem.dto;

public record AddElderRequest(String name, int age, Location homeAddress, boolean requireFrontSeat) {
}
