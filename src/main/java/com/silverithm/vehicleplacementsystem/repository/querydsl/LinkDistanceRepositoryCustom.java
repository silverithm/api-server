package com.silverithm.vehicleplacementsystem.repository.querydsl;

import java.util.Optional;

public interface LinkDistanceRepositoryCustom {
    Optional<Integer> findByStartNodeIdAndDestinationNodeId(Long startNodeId, Long destinationNodeId);
}
