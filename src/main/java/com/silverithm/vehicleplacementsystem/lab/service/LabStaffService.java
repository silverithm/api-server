package com.silverithm.vehicleplacementsystem.lab.service;

import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.lab.dto.LabStaffDto;
import com.silverithm.vehicleplacementsystem.lab.repository.LabStaffRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 같은 기관 직원 목록과 직원 id 검증 — 교육·회의 참석자를 고르고 확인하는 한 곳.
 *
 * <p>참석자 id는 클라이언트가 보내는 값이라 다른 기관 직원의 id를 넣으면 남의 직원 이름이 응답에 실릴 수 있다.
 * 그래서 모든 조회가 요청자 기관을 조건으로 건다. 소속이 아니면 존재 여부를 알려 주지 않게 "없음"과 같은 404를 쓴다.
 */
@Service
public class LabStaffService {

    private final LabAccessService access;
    private final LabStaffRepository staff;

    public LabStaffService(LabAccessService access, LabStaffRepository staff) {
        this.access = access;
        this.staff = staff;
    }

    /** 재직 중인 같은 기관 직원 — 이름은 암호화 열이라 DB가 아니라 여기서 이름순으로 정렬한다 */
    @Transactional(readOnly = true)
    public List<LabStaffDto.View> list() {
        Long companyId = access.requireCompanyId();
        return staff.findByCompanyAndStatus(companyId, Member.MemberStatus.ACTIVE).stream()
                .map(row -> new LabStaffDto.View(row.getId(), row.getName() == null ? "" : row.getName(),
                        row.getRole() == null ? null : row.getRole().name(), row.getPosition()))
                .sorted(Comparator.comparing(LabStaffDto.View::name).thenComparing(LabStaffDto.View::id))
                .toList();
    }

    /**
     * 모든 직원이 요청자 기관 소속인지 한 번에 확인하고 이름을 입력 순서대로 돌려준다.
     * 하나라도 다른 기관이거나 없는 id면 404 — 일부만 저장되는 일은 없다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> requireAllInCompany(Collection<Long> ids, Long companyId) {
        Map<Long, String> found = namesOf(ids, companyId);
        Map<Long, String> ordered = new LinkedHashMap<>();
        for (Long id : ids) {
            String name = found.get(id);
            if (name == null) {
                throw new CustomException("직원을 찾을 수 없습니다", HttpStatus.NOT_FOUND);
            }
            ordered.put(id, name);
        }
        return ordered;
    }

    /** 이 기관 소속인 직원의 이름(없는 id는 빠진다). 목록 응답의 이름 채우기가 한 번의 쿼리로 끝난다. */
    @Transactional(readOnly = true)
    public Map<Long, String> namesOf(Collection<Long> ids, Long companyId) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staff.findByCompanyAndIds(companyId, ids).stream()
                .collect(Collectors.toMap(LabStaffRepository.StaffRow::getId,
                        row -> row.getName() == null ? "" : row.getName(), (a, b) -> a, LinkedHashMap::new));
    }
}
