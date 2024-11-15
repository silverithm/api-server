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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NoArgsConstructor
@Getter
public class LinkDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String startNodeId;
    private String destinationNodeId;

    private int totalTime;
    private int totalDistance;

    public LinkDistance(String startNodeId, String destinationNodeId, int totalTime, int totalDistance) {
        this.startNodeId = startNodeId;
        this.destinationNodeId = destinationNodeId;
        this.totalTime = totalTime;
        this.totalDistance = totalDistance;
    }
}
