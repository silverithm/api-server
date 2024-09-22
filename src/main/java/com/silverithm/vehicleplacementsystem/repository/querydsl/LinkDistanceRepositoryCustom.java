package com.silverithm.vehicleplacementsystem.repository.querydsl;

import java.util.Optional;

public interface LinkDistanceRepositoryCustom {
    Optional<Integer> findByStartNodeIdAndDestinationNodeId(String startNodeId, String destinationNodeId);

    Optional<Integer> findDistanceByStartNodeIdAndDestinationNodeId(String startNodeId, String destinationNodeId);
}
