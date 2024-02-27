package com.silverithm.vehicleplacementsystem.repository.querydsl.impl;

import static com.silverithm.vehicleplacementsystem.entity.QLinkDistance.linkDistance;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.entity.QLinkDistance;
import com.silverithm.vehicleplacementsystem.repository.querydsl.LinkDistanceRepositoryCustom;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class LinkDistanceRepositoryCustomImpl implements LinkDistanceRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<Integer> findByStartNodeIdAndDestinationNodeId(Long startNodeId, Long destinationNodeId) {

        QLinkDistance linkDistance = QLinkDistance.linkDistance;

        return Optional.ofNullable(jpaQueryFactory.select(linkDistance.totalTime)
                .from(linkDistance)
                .where(startNodeIdEq(startNodeId))
                .where(destinationNodeIdEq(destinationNodeId))
                .fetchOne());

    }

    private BooleanBuilder startNodeIdEq(Long startNodeId) {
        return nullSafeBuilder(() -> linkDistance.startNodeId.eq(startNodeId));
    }

    private BooleanBuilder destinationNodeIdEq(Long destinationNodeId) {
        return nullSafeBuilder(() -> linkDistance.destinationNodeId.eq(destinationNodeId));
    }

    public static BooleanBuilder nullSafeBuilder(Supplier<BooleanExpression> f) {
        try {
            return new BooleanBuilder(f.get());
        } catch (IllegalArgumentException e) {
            return new BooleanBuilder();
        }
    }
}


