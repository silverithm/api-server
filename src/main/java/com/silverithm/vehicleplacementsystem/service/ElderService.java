package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
public class ElderService {

    @Autowired
    private ElderRepository elderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GeocodingService geocodingService;

    public void addElder(Long userId, AddElderRequest addElderRequest) throws Exception {

        Location homeAddress = geocodingService.getAddressCoordinates(addElderRequest.homeAddress());

        AppUser user = userRepository.findById(userId).orElseThrow();

        Elderly elderly = new Elderly(addElderRequest.name(), addElderRequest.homeAddress(), homeAddress,
                addElderRequest.requiredFrontSeat(), user);
        elderRepository.save(elderly);
    }


    public List<ElderlyDTO> getElders(Long userId) {

        List<Elderly> elderlys = elderRepository.findByUserId(userId);

        List<ElderlyDTO> elderlyDTOS = elderlys.stream()
                .map(elderly -> new ElderlyDTO(elderly.getId(), elderly.getName(), elderly.getHomeAddress(),
                        elderly.isRequiredFrontSeat(), elderly.getHomeAddressName()))
                .sorted(Comparator.comparing(ElderlyDTO::name))
                .collect(Collectors.toList());

        return elderlyDTOS;
    }

    public void deleteElder(Long elderId) {
        elderRepository.deleteById(elderId);
    }

    @Transactional
    public void updateElder(Long id, ElderUpdateRequestDTO elderUpdateRequestDTO) throws Exception {
        Location updatedHomeAddress = geocodingService.getAddressCoordinates(elderUpdateRequestDTO.homeAddress());
        Elderly elderly = elderRepository.findById(id).orElseThrow();
        elderly.update(elderUpdateRequestDTO.name(), elderUpdateRequestDTO.homeAddress(), updatedHomeAddress,
                elderUpdateRequestDTO.requiredFrontSeat());
    }

    @Transactional
    public void updateElderRequiredFrontSeat(Long id, ElderUpdateRequestDTO elderUpdateRequestDTO) {
        Elderly elderly = elderRepository.findById(id).orElseThrow();
        elderly.update(elderUpdateRequestDTO.requiredFrontSeat());
    }
}
