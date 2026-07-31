package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.FileAccessGuard;
import com.silverithm.vehicleplacementsystem.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileAccessGuard fileAccessGuard;

    /** 업로드 허용 카테고리 — 임의 경로로 저장 위치를 지정하지 못하게 한다. */
    private static final java.util.Set<String> ALLOWED_CATEGORIES =
            java.util.Set.of("templates", "attachments", "approvals", "signatures", "seals");

    /**
     * 파일 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "templates") String category) {

        try {
            if (!ALLOWED_CATEGORIES.contains(category)) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "허용되지 않는 업로드 분류입니다."));
            }

            log.info("[File API] 파일 업로드 요청: fileName={}, size={}, category={}",
                    file.getOriginalFilename(), file.getSize(), category);

            // 파일 크기 제한 (10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error", "파일 크기는 10MB를 초과할 수 없습니다."));
            }

            // 허용된 파일 확장자 검사
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                if (!isAllowedExtension(extension)) {
                    return ResponseEntity.badRequest()
                            .headers(getCorsHeaders())
                            .body(Map.of("error", "허용되지 않는 파일 형식입니다. (허용: hwp, hwpx, doc, docx, pdf, xls, xlsx, ppt, pptx, jpg, jpeg, png, gif)"));
                }
            }

            // 파일 저장
            String storedPath = fileStorageService.storeFile(file, category);

            // 아직 어떤 레코드에도 연결되지 않은 상태이므로 업로더 본인에게 한시적 접근을 허용
            fileAccessGuard.grantUploadGrace(userDetails, storedPath);

            log.info("[File API] 파일 업로드 완료: {}", storedPath);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "filePath", storedPath,
                            "fileName", file.getOriginalFilename(),
                            "fileSize", file.getSize(),
                            "message", "파일이 업로드되었습니다."
                    ));

        } catch (IOException e) {
            log.error("[File API] 파일 업로드 실패:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 업로드 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 파일 다운로드
     */
    @GetMapping("/download/**")
    public ResponseEntity<?> downloadFile(@AuthenticationPrincipal UserDetails userDetails,
                                          @RequestParam("path") String rawPath,
                                          @RequestParam(required = false) String fileName) {
        try {
            // 경로 형식 + 소속 기관 귀속 검증 (미통과 시 400/403)
            String path = fileAccessGuard.requireAccessible(userDetails, rawPath);

            log.info("[File API] 파일 다운로드 요청: path={}", path);

            if (!fileStorageService.fileExists(path)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = fileStorageService.loadFile(path);

            // 파일명 인코딩
            String encodedFileName = fileName != null
                    ? URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20")
                    : path.substring(path.lastIndexOf("/") + 1);

            // Content-Type 결정
            String contentType = determineContentType(path);

            HttpHeaders headers = getCorsHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName);
            headers.add(HttpHeaders.CONTENT_TYPE, contentType);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileContent);

        } catch (IOException e) {
            log.error("[File API] 파일 다운로드 실패:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 다운로드 중 오류가 발생했습니다."));
        }
    }

    /**
     * 파일 삭제
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteFile(@AuthenticationPrincipal UserDetails userDetails,
                                                          @RequestParam("path") String rawPath) {
        try {
            // 경로 형식 + 소속 기관 귀속 검증 (미통과 시 400/403)
            String path = fileAccessGuard.requireAccessible(userDetails, rawPath);

            log.info("[File API] 파일 삭제 요청: path={}", path);

            fileStorageService.deleteFile(path);

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of("success", true, "message", "파일이 삭제되었습니다."));

        } catch (IOException e) {
            log.error("[File API] 파일 삭제 실패:", e);
            return ResponseEntity.internalServerError()
                    .headers(getCorsHeaders())
                    .body(Map.of("error", "파일 삭제 중 오류가 발생했습니다."));
        }
    }

    private boolean isAllowedExtension(String extension) {
        return extension.matches("hwp|hwpx|doc|docx|pdf|xls|xlsx|ppt|pptx|jpg|jpeg|png|gif");
    }

    private String determineContentType(String path) {
        String extension = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "hwp" -> "application/x-hwp";
            case "hwpx" -> "application/hwp+zip";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return ResponseEntity.ok()
                .headers(getCorsHeaders())
                .build();
    }

    private HttpHeaders getCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return headers;
    }
}