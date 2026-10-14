package com.silverithm.vehicleplacementsystem.lab.repository;

import com.silverithm.vehicleplacementsystem.entity.Member;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 실험실이 직원(Member)을 읽는 전용 저장소.
 *
 * <p>Member의 permissions는 EAGER 컬렉션이라 엔티티로 읽으면 직원마다 쿼리가 더 나간다. 필요한 네 칸만
 * 투영으로 읽어 그 N+1을 피한다. 이름 칼럼은 암호화 컨버터가 걸린 열이라 DB에서 정렬하지 못한다 —
 * 정렬은 서비스가 한다. 모든 쿼리가 기관 조건을 갖는다.
 */
public interface LabStaffRepository extends Repository<Member, Long> {

    /** 재직 중인 직원 */
    @Query("SELECT m.id AS id, m.name AS name, m.role AS role, m.position AS position "
            + "FROM Member m WHERE m.company.id = :companyId AND m.status = :status")
    List<StaffRow> findByCompanyAndStatus(@Param("companyId") Long companyId,
                                          @Param("status") Member.MemberStatus status);

    /** 주어진 id 중 이 기관 직원인 사람(상태 무관 — 지금은 비활성인 사람의 지난 교육 이력도 읽혀야 한다) */
    @Query("SELECT m.id AS id, m.name AS name, m.role AS role, m.position AS position "
            + "FROM Member m WHERE m.company.id = :companyId AND m.id IN :ids")
    List<StaffRow> findByCompanyAndIds(@Param("companyId") Long companyId, @Param("ids") Collection<Long> ids);

    interface StaffRow {
        Long getId();

        String getName();

        Member.Role getRole();

        String getPosition();
    }
}
