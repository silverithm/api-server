package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDetailDTO;
import com.silverithm.vehicleplacementsystem.entity.DispatchHistory;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.DispatchHistoryRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Transactional
public class DispatchHistoryService {
    private final DispatchHistoryRepository repository;
    private final ObjectMapper objectMapper;  // JSON 변환용

    public DispatchHistoryService(DispatchHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void saveDispatchResult(List<AssignmentResponseDTO> result, String username) throws JsonProcessingException {

        DispatchHistory dispatchHistory = DispatchHistory.of(LocalDateTime.now(),
                objectMapper.writeValueAsString(result),
                (int) result.stream().map(AssignmentResponseDTO::employeeId).distinct().count(),
                result.stream().mapToInt(r -> r.assignmentElders().size()).sum(), result.get(0).dispatchType(),
                result.stream().mapToInt(AssignmentResponseDTO::time).sum(), username);

        repository.save(dispatchHistory);
    }

    public Page<DispatchHistoryDTO> getDispatchHistories(@AuthenticationPrincipal UserDetails userDetails, Pageable pageable) {

        return repository.findAllByUsername(userDetails.getUsername(), pageable)
                .map(this::convertToDTO);
    }

    private DispatchHistoryDTO convertToDTO(DispatchHistory dispatchHistory) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return new DispatchHistoryDTO(dispatchHistory.getId(), dispatchHistory.getCreatedAt().format(formatter),
                dispatchHistory.getTotalEmployees(), dispatchHistory.getTotalElders(), dispatchHistory.getTotalTime(),
                dispatchHistory.getDispatchType());
    }


    public DispatchHistoryDetailDTO getDispatchDetail(Long historyId) throws JsonProcessingException {
        DispatchHistory history = repository.findById(historyId)
                .orElseThrow(() -> new CustomException("History not found", HttpStatus.BAD_REQUEST));
        List<AssignmentResponseDTO> assignments = objectMapper.readValue(
                history.getDispatchResult(),
                new TypeReference<List<AssignmentResponseDTO>>() {
                }
        );
        return new DispatchHistoryDetailDTO(history.getId(), history.getCreatedAt(), assignments);
    }

    public ResponseEntity<Long> deleteHistory(Long id, UserDetails userDetails) {

        DispatchHistory history = repository.findById(id)
                .orElseThrow(() -> new CustomException("History not found", HttpStatus.BAD_REQUEST));

        log.info("history.getUsername() : {}", history.getUsername());
        log.info("userDetails.getUsername() : {}", userDetails.getUsername());

        if (!history.getUsername().equals(userDetails.getUsername())) {
            throw new CustomException("You are not authorized to delete this history", HttpStatus.FORBIDDEN);
        }

        repository.deleteById(id);
        return ResponseEntity.ok().body(id);
    }


}