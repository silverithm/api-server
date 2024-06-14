package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeocodingService {
    private String key;

    public GeocodingService(@Value("${googlemap.key}") String key) {
        this.key = key;
    }

    public Location getAddressCoordinates(String address)  throws Exception{
        try {
            String baseUrl = "https://maps.googleapis.com/maps/api/geocode/json";
            String finalUrl = baseUrl + "?address=" + address.replace(" ", "+") + "&key=" + key;

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(finalUrl, String.class);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode location = root.path("results").get(0).path("geometry").path("location");

            double latitude = location.path("lat").asDouble();
            double longitude = location.path("lng").asDouble();

            return new Location(latitude, longitude);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception();
        }
    }
}
