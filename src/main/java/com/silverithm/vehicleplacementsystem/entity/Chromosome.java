package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Chromosome {

    private List<List<Integer>> genes;
    private double fitness;
    private List<Double> departureTimes;
    private int totalElderly;

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
        // 차량배치에 참여하는 직원과 노인의 인덱스 번호를 직원 리스트, 노인 리스트와 함께 넘겨줌
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

        for (int i = 0; i < numEmployees; i++) {
            if (employeesCapacityLeft[i] > 0) {
                chromosome.get(i).add(elderlyIndices.get(0));
                employeesCapacityLeft[i]--;
                elderlyIndices.remove(0);
            }
        }

        for (int i = 0; i < numEmployees; i++) {
            if (employeesCapacityLeft[i] > 0) {
                chromosome.get(i).add(elderlyIndices.get(0));
                employeesCapacityLeft[i]--;
                elderlyIndices.remove(0);
            }
        }

        int startIndex = 0;
        Random rand = new Random();
        while (startIndex < elderlyIndices.size()) {
            int randIndex = rand.nextInt(numEmployees);
            if (employeesCapacityLeft[randIndex] > 0) {
                chromosome.get(randIndex).add(elderlyIndices.get(startIndex));
                employeesCapacityLeft[randIndex]--;
                startIndex++;
            }

        }

        genes = chromosome;

    }


    public static Chromosome copy(Chromosome original) {
        Chromosome copyObject = new Chromosome();

        // 깊은 복사를 위한 새로운 리스트 생성
        List<List<Integer>> newGenes = new ArrayList<>();
        for (List<Integer> gene : original.getGenes()) {
            newGenes.add(new ArrayList<>(gene));
        }
        copyObject.genes = newGenes;

        copyObject.totalElderly = original.getTotalElderly();
        copyObject.fitness = original.getFitness();
        copyObject.departureTimes = original.getDepartureTimes();

        return copyObject;
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


}
