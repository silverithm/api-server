package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.CompanyLibraryItem;
import com.silverithm.vehicleplacementsystem.repository.CompanyLibraryItemRepository;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.service.ResourceScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기관 전용 자료실 — 우리 기관 직원만 보고 올리는 내부 문서함.
 *
 * 파일 자체는 공용 업로드 API(/api/v1/files/upload)로 먼저 올리고,
 * 여기에는 그 결과(경로·이름·크기)와 제목·분류만 등록한다.
 */
@RestController
@RequestMapping("/api/v1/company-library")
@RequiredArgsConstructor
@Slf4j
public class CompanyLibraryController {

    private final CompanyLibraryItemRepository libraryRepository;
    private final CompanyRepository companyRepository;
    private final ResourceScopeGuard resourceScopeGuard;

    /** 우리 기관 자료 목록 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("기관을 찾을 수 없습니다: " + companyId));
        resourceScopeGuard.requireSameCompany(company);

        List<Map<String, Object>> items = libraryRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toMap).toList();

        return ResponseEntity.ok(Map.of("items", items));
    }

    /** 자료 등록 */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam Long companyId,
            @RequestBody Map<String, Object> body) {
        try {
            Company company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("기관을 찾을 수 없습니다: " + companyId));
            resourceScopeGuard.requireSameCompany(company);

            String title = str(body.get("title"));
            String filePath = str(body.get("filePath"));
            if (title == null || title.isBlank() || filePath == null || filePath.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "제목과 파일은 필수입니다."));
            }

            CompanyLibraryItem item = CompanyLibraryItem.builder()
                    .company(company)
                    .category(str(body.get("category")))
                    .title(title)
                    .description(str(body.get("description")))
                    .fileName(str(body.get("fileName")))
                    .fileSize(body.get("fileSize") == null ? 0L : Long.parseLong(String.valueOf(body.get("fileSize"))))
                    .filePath(filePath)
                    .uploaderId(str(body.get("uploaderId")))
                    .uploaderName(str(body.get("uploaderName")))
                    .build();

            CompanyLibraryItem saved = libraryRepository.save(item);
            log.info("[CompanyLibrary] 자료 등록: companyId={}, id={}", companyId, saved.getId());
            return ResponseEntity.ok(Map.of("success", true, "item", toMap(saved)));
        } catch (Exception e) {
            log.error("[CompanyLibrary] 자료 등록 오류:", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "자료 등록 중 오류가 발생했습니다."));
        }
    }

    /** 자료 수정 (제목·설명·분류만 — 파일 교체는 지우고 다시 올린다) */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CompanyLibraryItem item = libraryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자료를 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(item.getCompany());

        if (body.containsKey("title")) item.setTitle(str(body.get("title")));
        if (body.containsKey("description")) item.setDescription(str(body.get("description")));
        if (body.containsKey("category")) item.setCategory(str(body.get("category")));

        return ResponseEntity.ok(Map.of("success", true, "item", toMap(libraryRepository.save(item))));
    }

    /** 자료 삭제 */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        CompanyLibraryItem item = libraryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("자료를 찾을 수 없습니다: " + id));
        resourceScopeGuard.requireSameCompany(item.getCompany());

        libraryRepository.delete(item);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** null 값이 섞여 Map.of를 쓸 수 없어 HashMap으로 만든다 */
    private Map<String, Object> toMap(CompanyLibraryItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("category", item.getCategory());
        map.put("title", item.getTitle());
        map.put("description", item.getDescription());
        map.put("fileName", item.getFileName());
        map.put("fileSize", item.getFileSize());
        map.put("filePath", item.getFilePath());
        map.put("uploaderName", item.getUploaderName());
        map.put("createdAt", item.getCreatedAt());
        return map;
    }
}
