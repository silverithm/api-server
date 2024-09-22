package com.silverithm.vehicleplacementsystem.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Getter
@RequiredArgsConstructor
public class Couple {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "elder_1_node_id")
    private Elderly elder1;

    @OneToOne
    @JoinColumn(name = "elder_2_node_id")
    private Elderly elder2;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private AppUser user;


    public Couple(AppUser user, Elderly elder1, Elderly elder2) {
        this.user = user;
        this.elder1 = elder1;
        this.elder2 = elder2;
    }

    public void update(Elderly elder1, Elderly elder2) {
        this.elder1 = elder1;
        this.elder2 = elder2;
    }
}
