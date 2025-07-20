package com.silverithm.vehicleplacementsystem.repository.querydsl.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionBillingType;
import com.silverithm.vehicleplacementsystem.entity.SubscriptionStatus;
import com.silverithm.vehicleplacementsystem.repository.querydsl.UserRepositoryCustom;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

import static com.silverithm.vehicleplacementsystem.entity.QAppUser.appUser;
import static com.silverithm.vehicleplacementsystem.entity.QSubscription.subscription;

@Repository
public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public UserRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<AppUser> findUsersRequiringSubscriptionBilling(LocalDateTime currentDate) {
        return queryFactory
                .selectFrom(appUser)
                .join(appUser.subscription, subscription)
                .where(subscription.endDate.loe(currentDate), subscription.status.eq(SubscriptionStatus.ACTIVE),
                        subscription.billingType.ne(SubscriptionBillingType.FREE))
                .fetch();
    }
}
