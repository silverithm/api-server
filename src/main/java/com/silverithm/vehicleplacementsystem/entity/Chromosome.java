package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Chromosome implements Comparable<Chromosome> {

    private final List<Integer> genes;
    private double fitness;
    private List<Double> departureTimes;

    public Chromosome(List<EmployeeDTO> employees, List<ElderlyDTO> elderly, int requiredFrontSeat) {
        // 유전자 생성
        genes = new ArrayList<>();
        for (int i = 0; i < elderly.size(); i++) {
            genes.add(i);
        }


        // 셔플
        Collections.shuffle(genes);
//        for (int i = 0; i < genes.size(); i++) {
//            System.out.print(genes.get(i) + " ");
//        }
//        System.out.println();
    }

    public int getGeneLength() {
        return genes.size();
    }

    public int getGene(int index) {
        return genes.get(index);
    }

    public void setGene(int index, int value) {
        genes.set(index, value);
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
    }

    @Override
    public int compareTo(Chromosome other) {
        return Double.compare(other.fitness, fitness);
    }

}
