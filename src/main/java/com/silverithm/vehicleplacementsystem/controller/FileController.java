package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.service.FileAccessGuard;
import com.silverithm.vehicleplacementsystem.service.FileContentTypeResolver;
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
import java.util.Set;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;
    private final FileAccessGuard fileAccessGuard;

    /** 업로드 허용 카테고리 — 임의 경로로 저장 위치를 지정하지 못하게 한다. */
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("templates", "attachments", "approvals", "signatures", "seals", "profiles", "meetings");

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

            // 파일 크기 제한 — 결재 문서(approvals)는 스캔 PDF, 회의록(meetings)은 녹음 파일이 커서
            // 서버 멀티파트 한도(50MB)까지 허용
            long maxSize = ("approvals".equals(category) || "meetings".equals(category) ? 50L : 10L)
                    * 1024 * 1024;
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest()
                        .headers(getCorsHeaders())
                        .body(Map.of("error",
                                "파일 크기는 " + (maxSize / 1024 / 1024) + "MB를 초과할 수 없습니다."));
            }

            // 허용된 파일 확장자 검사 — 회의록(meetings)은 녹음 오디오도 받는다
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                // 확장자가 낯설어도 파일 내용이 이미지면 받아준다. 확장자가 없거나 엉뚱하게
                // 붙은 사진(카톡·스캔 앱 경유)이 문전에서 거부되지 않게 하려는 것이다.
                boolean allowed = isAllowedExtension(extension)
                        || ("meetings".equals(category) && isAllowedAudioExtension(extension))
                        || fileStorageService.probeContentType(file).startsWith("image/");
                if (!allowed) {
                    return ResponseEntity.badRequest()
                            .headers(getCorsHeaders())
                            .body(Map.of("error", "허용되지 않는 파일 형식입니다. (문서: hwp, hwpx, doc, docx, pdf, xls, xlsx, ppt, pptx / 이미지: jpg, png, gif, heic, heif, webp, avif, bmp, tiff 등)"));
                }
            }

            // 파일 저장 — HEIC/HEIF 사진이면 JPEG 사본이 함께 만들어지고 그쪽을 대표로 돌려준다.
            // (크롬·엣지·파이어폭스는 HEIC를 렌더링하지 못한다. 원본은 지우지 않는다.)
            FileStorageService.StoredUpload stored = fileStorageService.storeUpload(file, category);
            String storedPath = stored.path();

            // 아직 어떤 레코드에도 연결되지 않은 상태이므로 업로더 본인에게 한시적 접근을 허용
            // JPEG 사본을 만들었다면 원본에도 같은 유예를 준다(손으로 확인·복구할 때 필요).
            fileAccessGuard.grantUploadGrace(userDetails, storedPath);
            if (stored.isConverted()) {
                fileAccessGuard.grantUploadGrace(userDetails, stored.originalPath());
            }

            log.info("[File API] 파일 업로드 완료: {} (converted={})", storedPath, stored.isConverted());

            return ResponseEntity.ok()
                    .headers(getCorsHeaders())
                    .body(Map.of(
                            "success", true,
                            "filePath", storedPath,
                            "fileName", stored.fileName(),
                            "fileSize", stored.size(),
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
            // 내려줄 때도 실제 내용을 먼저 본다. 예전에 octet-stream으로 잘못 올라간 파일도
            // 여기서는 제 타입으로 나간다.
            String contentType = fileStorageService.resolveContentType(fileContent, path, null);

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

    /** 업로드 허용 문서 확장자. 이미지는 아래 isAllowedExtension()에서 따로 판단한다. */
    private boolean isAllowedDocumentExtension(String extension) {
        return extension.matches("hwp|hwpx|doc|docx|pdf|xls|xlsx|ppt|pptx");
    }

    /**
     * 업로드 허용 확장자.
     *
     * <p>이미지는 확장자를 일일이 나열하지 않고 {@link FileContentTypeResolver#isImageExtension}에
     * 위임한다. 예전에는 jpg/jpeg/png/gif만 받아서 아이폰이 찍은 heic 사진은 첨부 자체가 거부됐다.
     * 새 포맷이 생길 때마다 두 곳을 고치지 않도록 Content-Type 표와 목록을 하나로 묶었다.
     * svg는 그 표에서 이미지로 취급하지 않으므로 여기서도 자동으로 걸러진다.
     */
    private boolean isAllowedExtension(String extension) {
        return isAllowedDocumentExtension(extension) || FileContentTypeResolver.isImageExtension(extension);
    }

    /** 회의 녹음 파일 — meetings 카테고리에서만 허용 */
    private boolean isAllowedAudioExtension(String extension) {
        return extension.matches("m4a|mp3|wav|webm|ogg|aac");
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