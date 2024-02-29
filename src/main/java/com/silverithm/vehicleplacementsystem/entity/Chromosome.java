package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Chromosome implements Comparable<Chromosome> {

    private List<List<Integer>> genes;
    private double fitness;
    private List<Double> departureTimes;
    private int totalElderly;


    //        List<List<List<Integer>>> population = new ArrayList<>();
    //        for (int i = 0; i < popSize; i++) {
    //            List<Integer> elderlyIndices = new ArrayList<>();
    //            for (int j = 0; j < numElderly; j++) {
    //                elderlyIndices.add(j);
    //            }
    //            Collections.shuffle(elderlyIndices);
    //
    //            List<List<Integer>> chromosome = new ArrayList<>();
    //            int startIndex = 0;
    //            for (int e = 0; e < numEmployees; e++) {
    //                int numAssigned = (e < 2) ? 4 : 3;
    //                List<Integer> employeeAssignment = new ArrayList<>();
    //                for (int k = 0; k < numAssigned; k++) {
    //                    employeeAssignment.add(elderlyIndices.get(startIndex + k));
    //                }
    //                chromosome.add(employeeAssignment);
    //                startIndex += numAssigned;
    //            }
    //            population.add(chromosome);
    //        }
    //        return population;
    public Chromosome(List<EmployeeDTO> employees, List<ElderlyDTO> elderly) {

        totalElderly = elderly.size();
        int numEmployees = employees.size();
        List<Integer> elderlyIndices = new ArrayList<>();
        for (int i = 0; i < totalElderly; i++) {
            elderlyIndices.add(i);
        }
        Collections.shuffle(elderlyIndices);

        List<List<Integer>> chromosome = new ArrayList<>();
        int[] employeesCapacityLeft = new int[numEmployees];
        for (int e = 0; e < numEmployees; e++) {
            employeesCapacityLeft[e] = employees.get(e).maximumCapacity();
        }

        int startIndex = 0;
        while (startIndex < totalElderly) {
            boolean assigned = false;
            for (int e = 0; e < numEmployees && startIndex < totalElderly; e++) {
                if (chromosome.size() <= e) {
                    chromosome.add(new ArrayList<>());
                }
                if (employeesCapacityLeft[e] > 0) {
                    chromosome.get(e).add(elderlyIndices.get(startIndex));
                    employeesCapacityLeft[e]--;
                    startIndex++;
                    assigned = true;
                }
            }
            // 모든 직원의 capacity가 꽉 찼고, 여전히 할당되지 않은 노인이 있다면, 처리할 수 없는 상황임
            if (!assigned) {
                throw new IllegalStateException("모든 직원의 capacity가 초과되어 더 이상 노인을 할당할 수 없습니다.");
            }
        }

        genes = chromosome;

    }

    public int getGeneLength() {
        return genes.size();
    }

//    public int getGene(int index) {
//        return genes.get(index);
//    }
//
//    public void setGene(int index, int value) {
//        genes.set(index, value);
//    }


    @Override
    public int compareTo(Chromosome other) {
        return Double.compare(other.fitness, fitness);
    }

}
