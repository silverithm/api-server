package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElderRepository extends JpaRepository<Elderly, Long> {
    List<Elderly> findByUserId(Long userId);

    // 이름 컬럼은 암호화돼 있어 DB 정렬이 무의미하다 — 정렬은 조회 후 앱에서 한다
    List<Elderly> findByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    /**
     * 기관 소속 어르신 전체.
     * 레거시 데이터는 company 대신 등록자(user)로만 연결돼 있어 두 경로를 모두 포함한다.
     * (이름 정렬은 암호화 컬럼이라 호출자가 앱에서 한다)
     */
    @Query("SELECT e FROM Elderly e "
            + "LEFT JOIN e.company c "
            + "LEFT JOIN e.user u "
            + "LEFT JOIN u.company uc "
            + "WHERE c.id = :companyId OR uc.id = :companyId")
    List<Elderly> findAllInCompanyScope(@Param("companyId") Long companyId);

    /** 해당 어르신이 기관 범위에 속하는지 (레거시 user 연결 포함) */
    @Query("SELECT COUNT(e) > 0 FROM Elderly e "
            + "LEFT JOIN e.company c "
            + "LEFT JOIN e.user u "
            + "LEFT JOIN u.company uc "
            + "WHERE e.id = :elderId AND (c.id = :companyId OR uc.id = :companyId)")
    boolean existsInCompanyScope(@Param("elderId") Long elderId, @Param("companyId") Long companyId);
}
