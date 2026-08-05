package com.silverithm.vehicleplacementsystem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.entity.DispatchSetting;
import com.silverithm.vehicleplacementsystem.repository.DispatchSettingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배차 설정 API — 노선, 노선별 주·부운전자, 어르신 탑승 순서.
 *
 * 그동안 관리자 브라우저 localStorage에만 있어서 다른 기기에서는 설정이 비어 보였고,
 * 직원 앱은 주·부운전자를 알 수 없었다. 회사당 한 벌로 서버에 둔다.
 */
@RestController
@RequestMapping("/api/v1/dispatch-settings")
@RequiredArgsConstructor
@Slf4j
public class DispatchSettingController {

    private final DispatchSettingRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EMPTY_SETTINGS = "{\"routes\":[],\"seniors\":[]}";

    /** 회사의 배차 설정 조회. 없으면 빈 설정을 돌려준다(최초 진입). */
    @GetMapping
    public ResponseEntity<?> getSettings(@RequestParam Long companyId) {
        try {
            String json = repository.findByCompanyId(companyId)
                    .map(DispatchSetting::getSettingsJson)
                    .orElse(EMPTY_SETTINGS);
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("[배차설정] 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 설정을 불러오지 못했습니다"));
        }
    }

    /** 배차 설정 전체 저장 (회사당 한 벌 upsert) */
    @PutMapping
    @Transactional
    public ResponseEntity<?> saveSettings(@RequestParam Long companyId, @RequestBody JsonNode body) {
        try {
            if (body == null || !body.has("routes")) {
                return ResponseEntity.badRequest().body(Map.of("error", "routes가 필요합니다"));
            }
            String json = objectMapper.writeValueAsString(body);

            DispatchSetting setting = repository.findByCompanyId(companyId)
                    .orElseGet(() -> DispatchSetting.builder().companyId(companyId).build());
            setting.setSettingsJson(json);
            repository.save(setting);

            return ResponseEntity.ok(Map.of("saved", true));
        } catch (Exception e) {
            log.error("[배차설정] 저장 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 설정을 저장하지 못했습니다"));
        }
    }

    /**
     * 특정 직원이 어느 노선의 무슨 운전자인지 조회.
     * 직원 앱이 휴무 신청 전에 배차 충돌을 확인할 때 쓴다 — 설정 전체를 내려주지 않아도 된다.
     */
    @GetMapping("/driver-roles")
    public ResponseEntity<?> getDriverRoles(@RequestParam Long companyId, @RequestParam String memberName) {
        try {
            String json = repository.findByCompanyId(companyId)
                    .map(DispatchSetting::getSettingsJson)
                    .orElse(EMPTY_SETTINGS);
            JsonNode routes = objectMapper.readTree(json).path("routes");

            String target = memberName == null ? "" : memberName.trim();
            List<Map<String, Object>> roles = new ArrayList<>();

            for (JsonNode route : routes) {
                JsonNode drivers = route.path("routeDrivers");
                for (int i = 0; i < drivers.size(); i++) {
                    String name = drivers.get(i).path("driverName").asText("").trim();
                    if (!name.isEmpty() && name.equals(target)) {
                        // 같은 노선의 다른 운전자 — 동시 휴무 판정에 쓴다
                        List<String> others = new ArrayList<>();
                        for (int j = 0; j < drivers.size(); j++) {
                            if (j == i) continue;
                            String other = drivers.get(j).path("driverName").asText("").trim();
                            if (!other.isEmpty()) others.add(other);
                        }
                        roles.add(Map.of(
                                "routeName", route.path("name").asText(""),
                                "routeType", route.path("type").asText(""),
                                "roleIndex", i,
                                "roleLabel", i == 0 ? "주운전자" : "부" + i + "운전자",
                                "coDrivers", others));
                    }
                }
            }
            return ResponseEntity.ok(Map.of("roles", roles));
        } catch (Exception e) {
            log.error("[배차설정] 운전자 역할 조회 오류:", e);
            return ResponseEntity.ok(Map.of("roles", List.of()));
        }
    }

    /**
     * localStorage에만 있던 설정을 서버로 한 번 올리는 용도.
     * 서버에 이미 설정이 있으면 덮어쓰지 않는다.
     */
    @PostMapping("/migrate")
    @Transactional
    public ResponseEntity<?> migrate(@RequestParam Long companyId, @RequestBody JsonNode body) {
        try {
            if (repository.findByCompanyId(companyId).isPresent()) {
                return ResponseEntity.ok(Map.of("migrated", false, "reason", "already-exists"));
            }
            if (body == null || !body.has("routes") || body.path("routes").isEmpty()) {
                return ResponseEntity.ok(Map.of("migrated", false, "reason", "empty"));
            }
            repository.save(DispatchSetting.builder()
                    .companyId(companyId)
                    .settingsJson(objectMapper.writeValueAsString(body))
                    .build());
            return ResponseEntity.ok(Map.of("migrated", true));
        } catch (Exception e) {
            log.error("[배차설정] 이전 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 설정 이전에 실패했습니다"));
        }
    }
}
