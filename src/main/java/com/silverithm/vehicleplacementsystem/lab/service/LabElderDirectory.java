package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실험실이 어르신을 다루는 한 곳 — 기관 소속 확인과 이름 조회.
 *
 * <p>어르신 ID는 클라이언트가 보내는 값이라, 다른 기관의 ID를 넣으면 남의 어르신 기록이 만들어지거나
 * 읽힐 수 있다. 그래서 모든 저장·조회가 이 클래스로 소속을 확인한다. 소속이 아니면 존재 여부도
 * 알려 주지 않도록 "없음"과 같은 404를 쓴다. 레거시 데이터(기관 없이 등록자로만 연결)도 기존 어르신
 * 화면과 같은 범위 쿼리로 포함한다.
 */
@Component
@RequiredArgsConstructor
public class LabElderDirectory {

    private final ElderRepository elderRepository;

    /** 어르신이 요청자 기관 소속인지 확인한다. 아니면 404. */
    @Transactional(readOnly = true)
    public void requireInCompany(Long elderlyId, Long companyId) {
        if (elderlyId == null) {
            throw LabValidation.bad("어르신을 선택해 주세요");
        }
        if (!elderRepository.existsInCompanyScope(elderlyId, companyId)) {
            throw notFound();
        }
    }

    /** 기관 소속 어르신 전체의 이름 — 목록 응답에서 어르신마다 쿼리를 내지 않으려고 한 번에 읽는다. */
    @Transactional(readOnly = true)
    public Map<Long, String> namesInCompany(Long companyId) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Elderly elderly : elderRepository.findAllInCompanyScope(companyId)) {
            names.put(elderly.getId(), elderly.getName() == null ? "" : elderly.getName());
        }
        return names;
    }

    /** 이미 소속을 확인한 어르신 1명의 이름 */
    @Transactional(readOnly = true)
    public String nameOf(Long elderlyId) {
        return elderRepository.findById(elderlyId).map(Elderly::getName).orElse("");
    }

    static CustomException notFound() {
        return new CustomException("어르신을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
    }
}
