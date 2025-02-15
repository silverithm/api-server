package com.silverithm.vehicleplacementsystem.repository.querydsl;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import java.time.LocalDateTime;
import java.util.List;

public interface UserRepositoryCustom {
    List<AppUser> findUsersRequiringSubscriptionBilling(LocalDateTime currentDate);
}
