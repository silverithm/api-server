package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.Location;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class GeocodingService {
    private String key;

    public GeocodingService(@Value("${googlemap.key}") String key) {
        this.key = key;
    }

    public Location getAddressCoordinates(String address) throws Exception {
        try {
            String baseUrl = "https://maps.googleapis.com/maps/api/geocode/json";
            String finalUrl = baseUrl + "?address=" + cleanKoreanAddress(address).replace(" ", "+") + "&key=" + key;

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(finalUrl, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode location = root.path("results").get(0).path("geometry").path("location");

            double latitude = location.path("lat").asDouble();
            double longitude = location.path("lng").asDouble();

            return new Location(latitude, longitude);
        } catch (Exception e) {
            throw new Exception("Failed to get coordinates for address: " + address);
        }
    }

    private String cleanKoreanAddress(String address) {
        // Find the index of the first opening parenthesis
        int parenthesisIndex = address.indexOf("(");

        if (parenthesisIndex != -1) {
            // Return the part before the parenthesis, trimmed to remove any trailing spaces
            return address.substring(0, parenthesisIndex).trim();
        }

        // If no parentheses found, return the original address
        return address;
    }

}
