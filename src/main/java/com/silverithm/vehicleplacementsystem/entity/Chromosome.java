package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    public Chromosome(List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                      Map<Integer, List<Integer>> fixedAssignments) {

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
            chromosome.add(new ArrayList<>()); // 미리 리스트를 초기화
        }


        // 먼저 차량 배치에 참여할 직원과 노인을 정하고
        // 다음페이지로 넘어감
        // 직원과 노인의 아이디를 넘겨주는 것이 아니라
        // 차량배치에 참여하는 직원과 노인의 인덱스 번호를 직원 리스트, 노인 리스트와 함께 넘ㄱ줌
        // 백엔드는 이 인덱스를 가지고 Map을 생성함

        // 고정 할당 처리
        for (Map.Entry<Integer, List<Integer>> entry : fixedAssignments.entrySet()) {
            int employeeIndex = entry.getKey();
            List<Integer> fixedElderlyIds = entry.getValue();
            for (Integer elderlyId : fixedElderlyIds) {
                if (employeesCapacityLeft[employeeIndex] > 0) {
                    chromosome.get(employeeIndex).add(elderlyId);
                    employeesCapacityLeft[employeeIndex]--;
                    elderlyIndices.remove(elderlyId); // 이미 할당된 노인은 리스트에서 제거
                } else {
                    throw new IllegalStateException("직원 " + employeeIndex + "의 capacity가 초과되었습니다.");
                }
            }
        }

        int startIndex = 0;
        while (startIndex < elderlyIndices.size()) {
            boolean assigned = false;
            for (int e = 0; e < numEmployees && startIndex < elderlyIndices.size(); e++) {
                if (employeesCapacityLeft[e] > 0) {
                    chromosome.get(e).add(elderlyIndices.get(startIndex));
                    employeesCapacityLeft[e]--;
                    startIndex++;
                    assigned = true;
                }
            }
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
