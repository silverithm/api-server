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
        // 유전자 생성
//        for (int i = 0; i < elderly.size(); i++) {
//            genes.add(i);
//        }
        totalElderly = elderly.size();
        int numEmployees = employees.size();
        int totalElderly = elderly.size();

        List<Integer> elderlyIndices = new ArrayList<>();
        for (int i = 0; i < totalElderly; i++) {
            elderlyIndices.add(i);
        }
        Collections.shuffle(elderlyIndices); // 노인 인덱스를 랜덤하게 섞습니다.

        // 여기서부터 각 직원에게 노인을 배정하는 로직을 진행합니다.
        List<List<Integer>> chromosome = new ArrayList<>(); // 최종 결과를 저장할 리스트
        int baseNumAssigned = totalElderly / numEmployees;

        List<Integer> extraAssignments = new ArrayList<>();
        for (int i = 0; i < totalElderly % numEmployees; i++) {
            extraAssignments.add(i);
        }
        Collections.shuffle(extraAssignments);

        int startIndex = 0;
        for (int e = 0; e < numEmployees; e++) {
            int numAssigned = baseNumAssigned + (extraAssignments.contains(e) ? 1 : 0);
            List<Integer> employeeAssignment = new ArrayList<>();
            for (int k = 0; k < numAssigned; k++) {
                employeeAssignment.add(elderlyIndices.get(startIndex + k));
            }
            chromosome.add(employeeAssignment);
            startIndex += numAssigned;
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
