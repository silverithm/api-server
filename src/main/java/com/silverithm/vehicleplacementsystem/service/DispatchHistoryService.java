package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDTO;
import com.silverithm.vehicleplacementsystem.dto.DispatchHistoryDetailDTO;
import com.silverithm.vehicleplacementsystem.entity.DispatchHistory;
import com.silverithm.vehicleplacementsystem.repository.DispatchHistoryRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
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

    public void saveDispatchResult(List<AssignmentResponseDTO> result) throws JsonProcessingException {

        DispatchHistory dispatchHistory = DispatchHistory.of(LocalDateTime.now(),
                objectMapper.writeValueAsString(result),
                (int) result.stream().map(AssignmentResponseDTO::employeeId).distinct().count(),
                result.stream().mapToInt(r -> r.assignmentElders().size()).sum(), result.get(0).dispatchType(),
                result.stream().mapToInt(AssignmentResponseDTO::time).sum());

        repository.save(dispatchHistory);
    }

    public List<DispatchHistoryDTO> getDispatchHistories() {
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private DispatchHistoryDTO convertToDTO(DispatchHistory dispatchHistory) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return new DispatchHistoryDTO(dispatchHistory.getId(), dispatchHistory.getCreatedAt().format(formatter),
                dispatchHistory.getTotalEmployees(), dispatchHistory.getTotalElders(), dispatchHistory.getTotalTime(),
                dispatchHistory.getDispatchType());
    }


    public DispatchHistoryDetailDTO getDispatchDetail(Long historyId) throws JsonProcessingException {
        DispatchHistory history = repository.findById(historyId)
                .orElseThrow(() -> new RuntimeException("History not found"));
        List<AssignmentResponseDTO> assignments = objectMapper.readValue(
                history.getDispatchResult(),
                new TypeReference<List<AssignmentResponseDTO>>() {
                }
        );
        return new DispatchHistoryDetailDTO(history.getId(), history.getCreatedAt(), assignments);
    }
}