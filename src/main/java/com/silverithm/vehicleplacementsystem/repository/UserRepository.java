package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    AppUser findByEmail(String email);

    AppUser findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

}
