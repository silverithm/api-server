package com.silverithm.vehicleplacementsystem.repository.querydsl.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.silverithm.vehicleplacementsystem.entity.QLinkDistanceTime;
import com.silverithm.vehicleplacementsystem.repository.querydsl.LinkDistanceRepositoryCustom;
import java.util.Optional;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class LinkDistanceRepositoryCustomImpl implements LinkDistanceRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Optional<Integer> findByStartNodeIdAndDestinationNodeId(Long startNodeId, Long destinationNodeId) {

        QLinkDistanceTime linkDistanceTime = QLinkDistanceTime.linkDistanceTime;

        return Optional.ofNullable(jpaQueryFactory.select(linkDistanceTime.totalTime)
                .from(linkDistanceTime)
                .where(linkDistanceTime.startNode.id.eq(startNodeId))
                .where(linkDistanceTime.destinationNode.id.eq(destinationNodeId))
                .fetchOne());

    }
}
