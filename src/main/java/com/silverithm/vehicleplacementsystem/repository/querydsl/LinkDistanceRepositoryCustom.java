package com.silverithm.vehicleplacementsystem.repository.querydsl;

import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;

public interface LinkDistanceRepositoryCustom {

    Optional<Integer> findByStartNodeIdAndDestinationNodeId(String startNodeId, String destinationNodeId);

    Optional<Integer> findDistanceByStartNodeIdAndDestinationNodeId(String startNodeId, String destinationNodeId);

    Optional<LinkDistance> findNodeByStartNodeIdAndDestinationNodeId(String startNodeId, String destinationNodeId);

}
