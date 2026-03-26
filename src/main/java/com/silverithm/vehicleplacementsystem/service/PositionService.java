package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.PositionDTO;
import com.silverithm.vehicleplacementsystem.dto.PositionRequestDTO;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Member;
import com.silverithm.vehicleplacementsystem.entity.Position;
import com.silverithm.vehicleplacementsystem.repository.CompanyRepository;
import com.silverithm.vehicleplacementsystem.repository.MemberRepository;
import com.silverithm.vehicleplacementsystem.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionService {

    private final PositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final CompanyCodeService companyCodeService;

    @Transactional(readOnly = true)
    public List<PositionDTO> getPositions(Long companyId, String companyCode) {
        Company company = resolveCompany(companyId, companyCode);
        log.info("[Position Service] 직책 목록 조회: companyId={}", company.getId());
        return positionRepository.findByCompanyIdOrderBySortOrderAscNameAsc(company.getId())
                .stream()
                .map(PositionDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public PositionDTO createPosition(Long companyId, PositionRequestDTO request) {
        log.info("[Position Service] 직책 생성: companyId={}, name={}", companyId, request.getName());

        if (positionRepository.existsByCompanyIdAndName(companyId, request.getName())) {
            throw new RuntimeException("이미 존재하는 직책명입니다: " + request.getName());
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));

        Position position = Position.builder()
                .company(company)
                .name(request.getName())
                .description(request.getDescription())
                .memberRole(parseMemberRole(request.getMemberRole()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        Position saved = positionRepository.save(position);
        log.info("[Position Service] 직책 생성 완료: id={}", saved.getId());
        return PositionDTO.fromEntity(saved);
    }

    @Transactional
    public PositionDTO updatePosition(Long positionId, PositionRequestDTO request) {
        log.info("[Position Service] 직책 수정: id={}", positionId);

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("직책을 찾을 수 없습니다: " + positionId));

        // 이름 중복 검사 (자기 자신 제외)
        if (request.getName() != null &&
                positionRepository.existsByCompanyIdAndNameAndIdNot(
                        position.getCompany().getId(), request.getName(), positionId)) {
            throw new RuntimeException("이미 존재하는 직책명입니다: " + request.getName());
        }

        position.update(
                request.getName(),
                request.getDescription(),
                parseMemberRole(request.getMemberRole()),
                request.getSortOrder()
        );

        Position saved = positionRepository.save(position);
        log.info("[Position Service] 직책 수정 완료: id={}", saved.getId());
        return PositionDTO.fromEntity(saved);
    }

    @Transactional
    public void deletePosition(Long positionId) {
        log.info("[Position Service] 직책 삭제: id={}", positionId);

        Position position = positionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("직책을 찾을 수 없습니다: " + positionId));

        // 해당 직책을 사용 중인 회원들의 position_id를 null로 설정
        List<Member> members = memberRepository.findByPositionEntity(position);
        for (Member member : members) {
            member.setPositionEntity(null);
            member.setPosition(null);
        }
        memberRepository.saveAll(members);

        positionRepository.delete(position);
        log.info("[Position Service] 직책 삭제 완료: id={}", positionId);
    }

    @Transactional
    public void assignPositionToMember(Long memberId, Long positionId) {
        log.info("[Position Service] 직책 배정: memberId={}, positionId={}", memberId, positionId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다: " + memberId));

        if (positionId != null) {
            Position position = positionRepository.findById(positionId)
                    .orElseThrow(() -> new RuntimeException("직책을 찾을 수 없습니다: " + positionId));
            member.setPositionEntity(position);
            member.setPosition(position.getName());
        } else {
            member.setPositionEntity(null);
            member.setPosition(null);
        }

        memberRepository.save(member);
        log.info("[Position Service] 직책 배정 완료: memberId={}, positionId={}", memberId, positionId);
    }

    private Company resolveCompany(Long companyId, String companyCode) {
        if (companyId != null) {
            return companyRepository.findById(companyId)
                    .orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다: " + companyId));
        }

        String normalizedCompanyCode = companyCodeService.normalize(companyCode);
        if (!normalizedCompanyCode.isEmpty()) {
            return companyRepository.findByCompanyCodeIgnoreCase(normalizedCompanyCode)
                    .orElseThrow(() -> new RuntimeException("유효하지 않은 회사 코드입니다."));
        }

        throw new RuntimeException("companyId 또는 companyCode가 필요합니다.");
    }

    private Member.Role parseMemberRole(String memberRole) {
        if (memberRole == null || memberRole.isBlank()) {
            return null;
        }

        try {
            Member.Role parsedRole = Member.Role.valueOf(memberRole.trim().toUpperCase());
            if (parsedRole != Member.Role.CAREGIVER && parsedRole != Member.Role.OFFICE) {
                throw new RuntimeException("역할 기본 분류는 caregiver 또는 office만 가능합니다.");
            }
            return parsedRole;
        } catch (IllegalArgumentException error) {
            throw new RuntimeException("역할 기본 분류가 올바르지 않습니다: " + memberRole);
        }
    }
}
