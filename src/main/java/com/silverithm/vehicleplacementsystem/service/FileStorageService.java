package com.silverithm.vehicleplacementsystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStoragePath;

    @PostConstruct
    public void init() {
        this.fileStoragePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStoragePath);
            log.info("[FileStorage] 파일 저장 경로 초기화: {}", this.fileStoragePath);
        } catch (Exception e) {
            throw new RuntimeException("파일 저장 디렉토리를 생성할 수 없습니다.", e);
        }
    }

    /**
     * 파일 저장
     * @param file 업로드할 파일
     * @param subDirectory 하위 디렉토리 (예: "templates", "attachments")
     * @return 저장된 파일명 (UUID 포함)
     */
    public String storeFile(MultipartFile file, String subDirectory) throws IOException {
        // 원본 파일명
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("파일명이 유효하지 않습니다.");
        }

        // 파일 확장자 추출
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }

        // UUID로 고유 파일명 생성
        String storedFileName = UUID.randomUUID().toString() + extension;

        // 하위 디렉토리 생성
        Path targetDirectory = this.fileStoragePath.resolve(subDirectory);
        Files.createDirectories(targetDirectory);

        // 파일 저장
        Path targetLocation = targetDirectory.resolve(storedFileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("[FileStorage] 파일 저장 완료: {} -> {}", originalFileName, storedFileName);

        return subDirectory + "/" + storedFileName;
    }

    /**
     * 파일 읽기
     */
    public byte[] loadFile(String filePath) throws IOException {
        Path path = this.fileStoragePath.resolve(filePath).normalize();

        if (!path.startsWith(this.fileStoragePath)) {
            throw new SecurityException("파일 경로가 유효하지 않습니다.");
        }

        return Files.readAllBytes(path);
    }

    /**
     * 파일 삭제
     */
    public void deleteFile(String filePath) throws IOException {
        Path path = this.fileStoragePath.resolve(filePath).normalize();

        if (!path.startsWith(this.fileStoragePath)) {
            throw new SecurityException("파일 경로가 유효하지 않습니다.");
        }

        Files.deleteIfExists(path);
        log.info("[FileStorage] 파일 삭제: {}", filePath);
    }

    /**
     * 파일 존재 여부 확인
     */
    public boolean fileExists(String filePath) {
        Path path = this.fileStoragePath.resolve(filePath).normalize();
        return Files.exists(path);
    }
}