package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByUserId(Long userId);

    /**
     * 기관 소속 직원 전체.
     * 레거시 데이터는 company 대신 등록자(user)로만 연결돼 있어 두 경로를 모두 포함한다.
     */
    @Query("SELECT e FROM Employee e "
            + "LEFT JOIN e.company c "
            + "LEFT JOIN e.user u "
            + "LEFT JOIN u.company uc "
            + "WHERE c.id = :companyId OR uc.id = :companyId")
    List<Employee> findAllInCompanyScope(@Param("companyId") Long companyId);

    /** 해당 직원이 기관 범위에 속하는지 (레거시 user 연결 포함) */
    @Query("SELECT COUNT(e) > 0 FROM Employee e "
            + "LEFT JOIN e.company c "
            + "LEFT JOIN e.user u "
            + "LEFT JOIN u.company uc "
            + "WHERE e.id = :employeeId AND (c.id = :companyId OR uc.id = :companyId)")
    boolean existsInCompanyScope(@Param("employeeId") Long employeeId, @Param("companyId") Long companyId);

}
