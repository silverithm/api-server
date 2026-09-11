package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderResponse;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.ElderCareProfile;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Subscription;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.SubscriptionRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
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
    private MemberRepository memberRepository;

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

    @Transactional(readOnly = true)
    public List<CompanyElderResponse> getEldersByCompany(Long companyId) {
        // 케어 정보에는 주민번호가 들어 있다 — 남의 기관 목록이 열리지 않게 여기서 먼저 막는다
        resourceScopeGuard.requireSameCompany(companyId);

        // 이름은 암호화 컬럼이라 DB 정렬이 안 된다 — 복호화된 값으로 여기서 정렬한다
        return elderRepository.findWithCareProfileByCompanyId(companyId)
                .stream()
                .map(CompanyElderResponse::from)
                .sorted(Comparator.comparing(CompanyElderResponse::name))
                .collect(Collectors.toList());
    }

    public long getElderCountByCompany(Long companyId) {
        return elderRepository.countByCompanyId(companyId);
    }

    @Transactional
    public void addElderToCompany(Long companyId, CompanyElderRequestDTO request) throws Exception {
        // 조회·수정·삭제에만 검증이 붙어 있었다 — 생성도 막지 않으면 남의 기관에 어르신을 심을 수 있다
        resourceScopeGuard.requireSameCompany(companyId);
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회사입니다: " + companyId));

        Elderly elderly;
        if (request.homeAddress() != null && !request.homeAddress().isBlank()) {
            Location homeAddress = null; // 좌표 미사용 — 주소 좌표 변환 기능 제거 (배차 서비스 종료)
            elderly = new Elderly(request.name(), request.homeAddress(), homeAddress,
                    request.requiredFrontSeat(), company);
        } else {
            elderly = new Elderly(request.name(), request.requiredFrontSeat(), company);
        }
        applyCareProfile(elderly, request.careProfile());
        elderRepository.save(elderly);
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
        resourceScopeGuard.requireSameCompany(companyId);
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
            Elderly elderly = (request.homeAddress() != null && !request.homeAddress().isBlank())
                    ? new Elderly(name, request.homeAddress().trim(), null, request.requiredFrontSeat(), company)
                    : new Elderly(name, request.requiredFrontSeat(), company);
            applyCareProfile(elderly, request.careProfile());
            return elderly;
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

        // careProfile이 없는 요청은 이름만 고치는 옛 화면이다 — 케어 정보를 지우지 않는다
        applyCareProfile(elderly, request.careProfile());
    }

    /** 케어 정보만 갱신 (앱·웹 공용) */
    @Transactional
    public ElderCareProfileDTO updateCareProfile(Long elderId, ElderCareProfileRequest request) {
        Elderly elderly = loadElderInScope(elderId);
        if (request == null) {
            throw new CustomException("케어 정보가 비어 있습니다", HttpStatus.BAD_REQUEST);
        }
        applyCareProfile(elderly, request);
        // @LastModifiedDate는 flush 때 채워진다 — 그 전에 DTO를 만들면 '최종 수정'이 한 박자 늦게 나간다
        elderRepository.flush();
        return ElderCareProfileDTO.from(elderly.getCareProfile());
    }

    /**
     * 주민번호 전체 열람 — 관리자만. 열람 사실을 로그로 남긴다.
     *
     * <p>목록·상세에는 마스킹만 실리므로, 전체 값이 필요한 순간은 여기 한 곳뿐이다.
     * 그래야 "누가 언제 누구 것을 봤는지"가 한 줄로 남는다.
     */
    @Transactional(readOnly = true)
    public String revealResidentNumber(Long elderId) {
        Elderly elderly = loadElderInScope(elderId);
        String caller = requireAdminCaller();

        ElderCareProfile profile = elderly.getCareProfile();
        if (profile == null || !profile.hasResidentNumber()) {
            throw new CustomException("등록된 주민등록번호가 없습니다", HttpStatus.NOT_FOUND);
        }

        log.info("[PII] 주민번호 열람 elderId={} by={}", elderId, caller);
        return profile.getResidentNumber();
    }

    /** 기관 검증이 붙은 어르신 삭제. 케어 정보는 함께 지워진다(cascade). */
    @Transactional
    public void deleteCompanyElder(Long elderId) {
        Elderly elderly = loadElderInScope(elderId);
        elderRepository.delete(elderly);
    }

    private Elderly loadElderInScope(Long elderId) {
        Elderly elderly = elderRepository.findById(elderId)
                .orElseThrow(() -> new CustomException("존재하지 않는 어르신입니다: " + elderId, HttpStatus.NOT_FOUND));
        resourceScopeGuard.requireSameCompany(elderly.getCompany(),
                elderly.getUser() != null ? elderly.getUser().getCompany() : null);
        return elderly;
    }

    /**
     * 요청의 케어 정보를 어르신에게 반영한다.
     *
     * <p>null이면 아무것도 하지 않는다 — "안 보냈다"와 "비웠다"는 다르다.
     * 주민번호도 같은 규칙이라, null은 유지·빈 문자열은 삭제로 갈라 놓는다.
     */
    private void applyCareProfile(Elderly elderly, ElderCareProfileRequest request) {
        if (request == null) {
            return;
        }

        String normalized = ElderCareProfileSupport.normalizeResidentNumber(request.residentNumber());
        boolean touched = normalized != null;               // null = 미포함 → 기존 값 유지
        String stored = "".equals(normalized) ? null : normalized;

        ElderCareProfile profile = elderly.careProfileOrCreate();

        // 생년월일·성별을 안 보냈으면 주민번호에서 뽑는다. 요청에 주민번호가 없으면 이미 저장된 값을 쓴다
        String source = touched ? stored : profile.getResidentNumber();
        LocalDate birthDate = request.birthDate() != null
                ? request.birthDate() : ElderCareProfileSupport.deriveBirthDate(source);
        ElderCareProfile.Gender gender = request.gender() != null
                ? request.gender() : ElderCareProfileSupport.deriveGender(source);

        profile.apply(request.withIdentity(stored, birthDate, gender), touched);
    }

    /**
     * 요청자가 관리자인지 확인하고 식별자를 돌려준다.
     * 관리자 계정(AppUser)은 이메일로, 직원 계정(Member)은 역할로 판별한다.
     */
    private String requireAdminCaller() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new CustomException("인증 정보가 없습니다", HttpStatus.UNAUTHORIZED);
        }

        String username = authentication.getName();
        if (userRepository.findByEmail(username).isPresent()) {
            return username;
        }

        boolean isAdminMember = memberRepository.findByUsername(username)
                .map(member -> member.getRole() == Member.Role.ADMIN)
                .orElse(false);
        if (!isAdminMember) {
            log.warn("[PII] 관리자가 아닌 사용자의 주민번호 열람 시도: {}", username);
            throw new CustomException("주민등록번호는 관리자만 조회할 수 있습니다", HttpStatus.FORBIDDEN);
        }
        return username;
    }
}
