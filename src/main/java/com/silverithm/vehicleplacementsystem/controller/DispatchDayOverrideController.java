package com.silverithm.vehicleplacementsystem.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.entity.DispatchDayOverride;
import com.silverithm.vehicleplacementsystem.repository.DispatchDayOverrideRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 그날 하루치 배차 수정본 API.
 *
 * 노선 설정은 그대로 두고 그날 화면에만 얹는다 — "오늘은 저 어르신을 저 차에"가
 * 다음 날까지 따라가지 않도록. 수정본이 없는 날은 설정대로 계산된다.
 */
@RestController
@RequestMapping("/api/v1/dispatch-overrides")
@RequiredArgsConstructor
@Slf4j
public class DispatchDayOverrideController {

    private final DispatchDayOverrideRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String EMPTY = "[]";

    /** 하루치 조회. 수정한 적이 없으면 빈 배열 — 화면은 설정대로 그린다. */
    @GetMapping
    public ResponseEntity<?> getOne(@RequestParam Long companyId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            String json = repository.findByCompanyIdAndDispatchDate(companyId, date)
                    .map(DispatchDayOverride::getOverridesJson)
                    .orElse(EMPTY);
            return ResponseEntity.ok(Map.of("date", date.toString(), "assignments", objectMapper.readTree(json)));
        } catch (Exception e) {
            log.error("[배차 수정본] 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 수정본을 불러오지 못했습니다"));
        }
    }

    /**
     * 기간 조회 — 달력·목록이 한 달을 한 번에 그릴 때 하루씩 서른 번 묻지 않게.
     * 수정본이 있는 날만 담아 준다.
     */
    @GetMapping("/range")
    public ResponseEntity<?> getRange(@RequestParam Long companyId,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            List<DispatchDayOverride> rows =
                    repository.findByCompanyIdAndDispatchDateBetween(companyId, startDate, endDate);

            Map<String, JsonNode> byDate = new LinkedHashMap<>();
            for (DispatchDayOverride row : rows) {
                byDate.put(row.getDispatchDate().toString(), objectMapper.readTree(row.getOverridesJson()));
            }
            return ResponseEntity.ok(Map.of("days", byDate));
        } catch (Exception e) {
            log.error("[배차 수정본] 기간 조회 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 수정본을 불러오지 못했습니다"));
        }
    }

    /**
     * 하루치 저장 (덮어쓰기).
     *
     * 빈 배열을 보내면 그 날의 수정본을 지운다 — "원래대로"가 삭제와 같은 뜻이 되도록.
     */
    @PutMapping
    @Transactional
    public ResponseEntity<?> save(@RequestParam Long companyId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestBody JsonNode body) {
        try {
            JsonNode assignments = body != null && body.has("assignments") ? body.get("assignments") : body;
            if (assignments == null || !assignments.isArray()) {
                return ResponseEntity.badRequest().body(Map.of("error", "assignments 배열이 필요합니다"));
            }

            if (assignments.isEmpty()) {
                repository.findByCompanyIdAndDispatchDate(companyId, date).ifPresent(repository::delete);
                return ResponseEntity.ok(Map.of("saved", true, "cleared", true));
            }

            DispatchDayOverride row = repository.findByCompanyIdAndDispatchDate(companyId, date)
                    .orElseGet(() -> DispatchDayOverride.builder()
                            .companyId(companyId)
                            .dispatchDate(date)
                            .build());
            row.setOverridesJson(objectMapper.writeValueAsString(assignments));
            repository.save(row);

            return ResponseEntity.ok(Map.of("saved", true, "count", assignments.size()));
        } catch (Exception e) {
            log.error("[배차 수정본] 저장 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "배차 수정본을 저장하지 못했습니다"));
        }
    }

    /** 그날 수정본을 지운다 — 설정대로 되돌린다 */
    @DeleteMapping
    @Transactional
    public ResponseEntity<?> clear(@RequestParam Long companyId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        repository.findByCompanyIdAndDispatchDate(companyId, date).ifPresent(repository::delete);
        return ResponseEntity.ok(Map.of("cleared", true));
    }
}
