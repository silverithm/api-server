package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
