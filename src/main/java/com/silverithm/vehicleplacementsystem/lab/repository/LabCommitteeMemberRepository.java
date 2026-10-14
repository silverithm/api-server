package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.lab.entity.LabCommitteeMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCommitteeMemberRepository extends JpaRepository<LabCommitteeMember, Long> {

    List<LabCommitteeMember> findByCompanyIdOrderByIdAsc(Long companyId);

    /** 주어진 id 중 이 기관 위원 — 다른 기관 id는 결과에 없다 */
    List<LabCommitteeMember> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    Optional<LabCommitteeMember> findByIdAndCompanyId(Long id, Long companyId);
}
