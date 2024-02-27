package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.NoArgsConstructor;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NoArgsConstructor
public class LinkDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long startNodeId;
    private Long destinationNodeId;

    private int totalTime;

    public LinkDistance(Long startNodeId, Long destinationNodeId, int totalTime) {
        this.startNodeId = startNodeId;
        this.destinationNodeId = destinationNodeId;
        this.totalTime = totalTime;
    }
}
