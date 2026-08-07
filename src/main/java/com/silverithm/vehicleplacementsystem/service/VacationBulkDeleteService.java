package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.VacationBulkActionResponseDTO;
import com.silverithm.vehicleplacementsystem.entity.VacationRequest;
import com.silverithm.vehicleplacementsystem.repository.VacationRequestRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴무 일괄 삭제 — 관리자가 조회 기간 안의 휴무를 한 번에 정리할 때 쓴다.
 * 승인/거절과 달리 되돌릴 수 없으므로 관리자 권한 확인은 호출부(컨트롤러)에서 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacationBulkDeleteService {

    private final VacationRequestRepository vacationRequestRepository;

    @Transactional
    public VacationBulkActionResponseDTO bulkDeleteVacations(List<Long> vacationIds) {
        if (vacationIds == null || vacationIds.isEmpty()) {
            return VacationBulkActionResponseDTO.builder()
                    .totalRequested(0)
                    .successCount(0)
                    .failureCount(0)
                    .message("처리할 휴무가 없습니다")
                    .build();
        }
        log.info("[Vacation Service] 휴무 일괄 삭제: {}건", vacationIds.size());

        Map<Long, VacationRequest> found = vacationRequestRepository.findAllById(vacationIds).stream()
                .collect(Collectors.toMap(VacationRequest::getId, Function.identity()));

        List<VacationRequest> toDelete = new ArrayList<>();
        List<Long> successIds = new ArrayList<>();
        List<Long> failureIds = new ArrayList<>();
        Map<Long, String> failureReasons = new HashMap<>();

        for (Long id : vacationIds) {
            VacationRequest vacation = found.get(id);
            if (vacation == null) {
                failureIds.add(id);
                failureReasons.put(id, "휴무 신청을 찾을 수 없습니다");
                continue;
            }
            toDelete.add(vacation);
            successIds.add(id);
        }

        if (!toDelete.isEmpty()) {
            vacationRequestRepository.deleteAll(toDelete);
        }

        return VacationBulkActionResponseDTO.builder()
                .totalRequested(vacationIds.size())
                .successCount(successIds.size())
                .failureCount(failureIds.size())
                .successIds(successIds)
                .failureIds(failureIds)
                .failureReasons(failureReasons)
                .message(String.format("%d건 중 %d건 삭제 완료", vacationIds.size(), successIds.size()))
                .build();
    }
}
