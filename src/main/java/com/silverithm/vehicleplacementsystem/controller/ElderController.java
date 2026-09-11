package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderResponse;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderCareProfileRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ElderController {

    private final ElderService elderService;

    @PostMapping("/api/v1/elder/{userId}")
    public String elderAdd(@PathVariable("userId") final Long userId, @RequestBody AddElderRequest addElderRequest)
            throws Exception {
        elderService.addElder(userId, addElderRequest);
        return "Success";
    }

    @GetMapping("/api/v1/elders/{userId}")
    public List<ElderlyDTO> getElders(@PathVariable("userId") final Long userId) {
        return elderService.getElders(userId);
    }

    @DeleteMapping("/api/v1/elder/{id}")
    public String deleteElder(@PathVariable("id") final Long id) {
        elderService.deleteElder(id);
        return "Success";
    }

    @PutMapping("/api/v1/elder/{id}")
    public String updateElder(@PathVariable("id") final Long id,
                              @RequestBody ElderUpdateRequestDTO elderUpdateRequestDTO) throws Exception {
        elderService.updateElder(id, elderUpdateRequestDTO);
        return "Success";
    }

    @PutMapping("/api/v1/elder/frontseat/{id}")
    public String updateElderRequiredFrontSeat(@PathVariable("id") final Long id,
                                               @RequestBody ElderUpdateRequestDTO elderUpdateRequestDTO)
            throws Exception {
        elderService.updateElderRequiredFrontSeat(id, elderUpdateRequestDTO);
        return "Success";
    }

    @PostMapping("/api/v1/elders/bulk")
    public String bulkAddElders(@AuthenticationPrincipal UserDetails userDetails,
                                @RequestBody List<AddElderRequest> elderRequests) throws Exception {
        elderService.bulkAddElders(userDetails, elderRequests);
        return "Success";
    }

    // ==================== Company 기반 어르신 관리 API ====================

    @GetMapping("/api/v1/elders/company/{companyId}")
    public ResponseEntity<Map<String, Object>> getEldersByCompany(@PathVariable("companyId") Long companyId) {
        List<CompanyElderResponse> elders = elderService.getEldersByCompany(companyId);
        return ResponseEntity.ok(Map.of("elders", elders));
    }

    @GetMapping("/api/v1/elders/company/{companyId}/count")
    public ResponseEntity<Map<String, Long>> getElderCountByCompany(@PathVariable("companyId") Long companyId) {
        long count = elderService.getElderCountByCompany(companyId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/api/v1/elders/company/{companyId}")
    public ResponseEntity<String> addElderToCompany(@PathVariable("companyId") Long companyId,
                                                     @RequestBody CompanyElderRequestDTO request) throws Exception {
        elderService.addElderToCompany(companyId, request);
        return ResponseEntity.ok("Success");
    }

    @PostMapping("/api/v1/elders/company/{companyId}/bulk")
    public ResponseEntity<Map<String, Object>> bulkAddEldersToCompany(@PathVariable("companyId") Long companyId,
                                                                      @RequestBody List<CompanyElderRequestDTO> requests) {
        int created = elderService.bulkAddEldersToCompany(companyId, requests);
        return ResponseEntity.ok(Map.of("created", created));
    }

    @PutMapping("/api/v1/elders/company/elder/{id}")
    public ResponseEntity<String> updateCompanyElder(@PathVariable("id") Long id,
                                                      @RequestBody CompanyElderRequestDTO request) throws Exception {
        elderService.updateCompanyElder(id, request);
        return ResponseEntity.ok("Success");
    }

    /** 케어 정보만 저장 — 이름·주소는 건드리지 않는다(앱·웹 공용) */
    @PutMapping("/api/v1/elders/company/elder/{id}/care-profile")
    public ResponseEntity<ElderCareProfileDTO> updateCareProfile(
            @PathVariable("id") Long id,
            // merge=true는 엑셀 채우기 — 값이 없는 칸은 저장된 값을 그대로 둔다.
            // 기본은 덮어쓰기다(화면 수정은 끈 스위치·지운 메모가 저장돼야 한다).
            @RequestParam(name = "merge", defaultValue = "false") boolean merge,
            @RequestBody ElderCareProfileRequest request) {
        return ResponseEntity.ok(elderService.updateCareProfile(id, request, merge));
    }

    /** 주민번호 전체 열람 — 관리자만. 목록·상세에는 마스킹 값만 나간다. */
    @GetMapping("/api/v1/elders/company/elder/{id}/resident-number")
    public ResponseEntity<Map<String, String>> getResidentNumber(@PathVariable("id") Long id) {
        return ResponseEntity.ok(Map.of("residentNumber", elderService.revealResidentNumber(id)));
    }

    /**
     * 기관 검증이 붙은 삭제. 기존 {@code /api/v1/elder/{id}}는 검증이 없어
     * ID만 알면 남의 기관 어르신이 지워졌다 — 화면은 이 경로를 쓴다.
     */
    @DeleteMapping("/api/v1/elders/company/elder/{id}")
    public ResponseEntity<String> deleteCompanyElder(@PathVariable("id") Long id) {
        elderService.deleteCompanyElder(id);
        return ResponseEntity.ok("Success");
    }
}
