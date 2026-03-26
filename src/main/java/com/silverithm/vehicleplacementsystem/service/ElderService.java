package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
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
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CompanyRepository companyRepository;

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

    public void bulkAddElders(UserDetails userDetails, List<AddElderRequest> elderRequests) throws Exception {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        for (AddElderRequest elderRequest : elderRequests) {
            Location homeAddress = geocodingService.getAddressCoordinates(elderRequest.homeAddress());

            Elderly elderly = new Elderly(elderRequest.name(), elderRequest.homeAddress(), homeAddress,
                    elderRequest.requiredFrontSeat(), user);

            elderRepository.save(elderly);
        }
    }

    // ==================== Company 기반 어르신 관리 ====================

    public List<ElderlyDTO> getEldersByCompany(Long companyId) {
        return elderRepository.findByCompanyIdOrderByNameAsc(companyId)
                .stream()
                .map(ElderlyDTO::from)
                .collect(Collectors.toList());
    }

    public long getElderCountByCompany(Long companyId) {
        return elderRepository.countByCompanyId(companyId);
    }

    public void addElderToCompany(Long companyId, CompanyElderRequestDTO request) throws Exception {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        if (request.homeAddress() != null && !request.homeAddress().isBlank()) {
            Location homeAddress = geocodingService.getAddressCoordinates(request.homeAddress());
            Elderly elderly = new Elderly(request.name(), request.homeAddress(), homeAddress,
                    request.requiredFrontSeat(), company);
            elderRepository.save(elderly);
        } else {
            Elderly elderly = new Elderly(request.name(), request.requiredFrontSeat(), company);
            elderRepository.save(elderly);
        }
    }

    @Transactional
    public void updateCompanyElder(Long id, CompanyElderRequestDTO request) throws Exception {
        Elderly elderly = elderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 어르신입니다: " + id));

        if (request.homeAddress() != null && !request.homeAddress().isBlank()) {
            Location updatedHomeAddress = geocodingService.getAddressCoordinates(request.homeAddress());
            elderly.update(request.name(), request.homeAddress(), updatedHomeAddress, request.requiredFrontSeat());
        } else {
            elderly.updateName(request.name());
            elderly.update(request.requiredFrontSeat());
        }
    }
}
