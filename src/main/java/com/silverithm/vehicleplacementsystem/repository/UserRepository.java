package com.silverithm.vehicleplacementsystem.repository;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.repository.querydsl.LinkDistanceRepositoryCustom;
import com.silverithm.vehicleplacementsystem.repository.querydsl.UserRepositoryCustom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long>, UserRepositoryCustom {

    Optional<AppUser> findByEmail(String email);

    AppUser findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByCustomerKey(String customerKey);

    Optional<AppUser> findByRefreshToken(String refreshToken);

    Optional<AppUser> findByEmailAndDeletedAtIsNull(
            String email);

    boolean existsByEmailAndDeletedAtIsNull(
            String email);

    Optional<AppUser> findByUsernameAndDeletedAtIsNull(String username);
}
