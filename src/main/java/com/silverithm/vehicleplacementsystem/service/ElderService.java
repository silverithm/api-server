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
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ResourceScopeGuard resourceScopeGuard;

    public void addElder(Long userId, AddElderRequest addElderRequest) throws Exception {

        Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)

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
        Location updatedHomeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
        Elderly elderly = elderRepository.findById(id).orElseThrow();
        resourceScopeGuard.requireSameCompany(elderly.getCompany(),
                elderly.getUser() != null ? elderly.getUser().getCompany() : null);
        elderly.update(elderUpdateRequestDTO.name(), elderUpdateRequestDTO.homeAddress(), updatedHomeAddress,
                elderUpdateRequestDTO.requiredFrontSeat());
    }

    @Transactional
    public void updateElderRequiredFrontSeat(Long id, ElderUpdateRequestDTO elderUpdateRequestDTO) {
        Elderly elderly = elderRepository.findById(id).orElseThrow();
        resourceScopeGuard.requireSameCompany(elderly.getCompany(),
                elderly.getUser() != null ? elderly.getUser().getCompany() : null);
        elderly.update(elderUpdateRequestDTO.requiredFrontSeat());
    }

    public void bulkAddElders(UserDetails userDetails, List<AddElderRequest> elderRequests) throws Exception {
        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        for (AddElderRequest elderRequest : elderRequests) {
            Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)

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
            Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
            Elderly elderly = new Elderly(request.name(), request.homeAddress(), homeAddress,
                    request.requiredFrontSeat(), company);
            elderRepository.save(elderly);
        } else {
            Elderly elderly = new Elderly(request.name(), request.requiredFrontSeat(), company);
            elderRepository.save(elderly);
        }
    }

    /**
     * 어르신 대량 등록 (엑셀 업로드). 전체가 한 트랜잭션이다 —
     * 도중에 실패하면 아무도 등록되지 않아, 몇 명까지 들어갔는지 세어볼 필요가 없다.
     */
    @Transactional
    public int bulkAddEldersToCompany(Long companyId, List<CompanyElderRequestDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            return 0;
        }
        if (requests.size() > 500) {
            throw new CustomException("한 번에 500명까지 등록할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        List<Elderly> elders = requests.stream().map(request -> {
            if (request.name() == null || request.name().isBlank()) {
                throw new CustomException("이름이 비어 있는 행이 있어 등록을 중단했습니다.", HttpStatus.BAD_REQUEST);
            }
            String name = request.name().trim();
            if (name.length() > 50) {
                throw new CustomException("이름이 50자를 넘는 행이 있어 등록을 중단했습니다: " + name, HttpStatus.BAD_REQUEST);
            }
            if (request.homeAddress() != null && request.homeAddress().length() > 200) {
                throw new CustomException("주소가 200자를 넘는 행이 있어 등록을 중단했습니다: " + name, HttpStatus.BAD_REQUEST);
            }
            if (request.homeAddress() != null && !request.homeAddress().isBlank()) {
                return new Elderly(name, request.homeAddress().trim(), null, request.requiredFrontSeat(), company);
            }
            return new Elderly(name, request.requiredFrontSeat(), company);
        }).collect(Collectors.toList());

        elderRepository.saveAll(elders);
        return elders.size();
    }

    @Transactional
    public void updateCompanyElder(Long id, CompanyElderRequestDTO request) throws Exception {
        Elderly elderly = elderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 어르신입니다: " + id));
        resourceScopeGuard.requireSameCompany(elderly.getCompany(),
                elderly.getUser() != null ? elderly.getUser().getCompany() : null);

        if (request.homeAddress() != null && !request.homeAddress().isBlank()) {
            Location updatedHomeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
            elderly.update(request.name(), request.homeAddress(), updatedHomeAddress, request.requiredFrontSeat());
        } else {
            elderly.updateName(request.name());
            elderly.update(request.requiredFrontSeat());
        }
    }
}
