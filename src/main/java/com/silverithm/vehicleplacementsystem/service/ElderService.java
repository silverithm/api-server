package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ElderService {

    @Autowired
    private ElderRepository elderRepository;

    private String key;

    public ElderService(@Value("${googlemap.key}") String key) {
        this.key = key;
    }

    public void addElder(AddElderRequest addElderRequest) {

        Location homeAddress = getAddressCoordinates(addElderRequest.homeAddress());

        Elderly elderly = new Elderly(addElderRequest.name(), addElderRequest.age(), homeAddress,
                addElderRequest.requireFrontSeat());
        elderRepository.save(elderly);
    }

    public Location getAddressCoordinates(String address) {
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
            return null; // 오류 처리는 상황에 맞게 조정
        }
    }


}
