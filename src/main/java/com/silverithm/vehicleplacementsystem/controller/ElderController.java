package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AddEmployeeRequest;
import com.silverithm.vehicleplacementsystem.dto.CompanyElderRequestDTO;
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
        List<ElderlyDTO> elders = elderService.getEldersByCompany(companyId);
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

    @PutMapping("/api/v1/elders/company/elder/{id}")
    public ResponseEntity<String> updateCompanyElder(@PathVariable("id") Long id,
                                                      @RequestBody CompanyElderRequestDTO request) throws Exception {
        elderService.updateCompanyElder(id, request);
        return ResponseEntity.ok("Success");
    }
}
