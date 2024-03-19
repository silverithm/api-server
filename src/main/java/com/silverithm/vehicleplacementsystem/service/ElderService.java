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

    @Autowired
    private GeocodingService geocodingService;

    public void addElder(AddElderRequest addElderRequest) {

        Location homeAddress = geocodingService.getAddressCoordinates(addElderRequest.homeAddress());

        Elderly elderly = new Elderly(addElderRequest.name(), homeAddress,
                addElderRequest.requireFrontSeat());
        elderRepository.save(elderly);
    }


}
