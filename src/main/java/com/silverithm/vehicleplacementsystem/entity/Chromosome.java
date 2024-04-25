package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class Chromosome {

    private List<List<Integer>> genes;
    private double fitness;
    private List<Double> departureTimes;
    private int totalElderly;

    public Chromosome(List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                      Map<Integer, List<Integer>> fixedAssignments) {

        int numEmployees = employees.size();
        int totalElderly = elderly.size();

        List<Integer> elderlyIndexs = createRandomElderlyIndexs(totalElderly);
        int[] employeesCapacityLeft = initializeEmployeesCapacityLeft(employees);
        List<List<Integer>> chromosome = initializeChromosomeWithMaximumCapacity(employees, numEmployees,
                employeesCapacityLeft);

        fixElderlyAtChromosome(fixedAssignments, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixInitialChromosome(employees, numEmployees, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixInitialChromosome(employees, numEmployees, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixRandomElderlyIndexAtChromosome(numEmployees, employeesCapacityLeft, elderlyIndexs, chromosome);
        removeEmptyChromosome(chromosome);

        genes = chromosome;

    }

    private int[] initializeEmployeesCapacityLeft(List<EmployeeDTO> employees) {

        int[] employeesCapacityLeft = new int[employees.size()];

        for (int i = 0; i < employees.size(); i++) {
            employeesCapacityLeft[i] = employees.get(i).maximumCapacity();
        }

        return employeesCapacityLeft;
    }

    public void removeEmptyChromosome(List<List<Integer>> chromosome) {
        for (int i = 0; i < chromosome.size(); i++) {
            for (int j = 0; j < chromosome.get(i).size(); j++) {
                if (chromosome.get(i).get(j) == -1) {
                    chromosome.get(i).remove(j);
                }
            }
        }
    }

    public void fixRandomElderlyIndexAtChromosome(int numEmployees, int[] employeesCapacityLeft,
                                                  List<Integer> elderlyIndexs,
                                                  List<List<Integer>> chromosome) {
        int startIndex = 0;
        Random rand = new Random();
        while (startIndex < elderlyIndexs.size()) {
            int randIndex = rand.nextInt(numEmployees);
            for (int i = 0; i < chromosome.get(randIndex).size(); i++) {
                if (chromosome.get(randIndex).get(i) == -1 && employeesCapacityLeft[randIndex] > 0) {
                    chromosome.get(randIndex).set(i, Integer.valueOf(elderlyIndexs.get(startIndex)));
                    employeesCapacityLeft[randIndex]--;
                    startIndex++;
                    break;
                }
            }
        }
    }

    public void fixInitialChromosome(List<EmployeeDTO> employees, int numEmployees, int[] employeesCapacityLeft,
                                     List<Integer> elderlyIndexs, List<List<Integer>> chromosome) {
        for (int i = 0; i < numEmployees; i++) {
            for (int j = 0; j < employees.get(i).maximumCapacity(); j++) {
                if (employeesCapacityLeft[i] > 0 && elderlyIndexs.size() > 0 && chromosome.get(i).get(j) == -1) {
                    chromosome.get(i).set(j, elderlyIndexs.get(0));
                    employeesCapacityLeft[i]--;
                    elderlyIndexs.remove(0);
                    break;
                }
            }
        }
    }

    public void fixElderlyAtChromosome(Map<Integer, List<Integer>> fixedAssignments, int[] employeesCapacityLeft,
                                       List<Integer> elderlyIndexs, List<List<Integer>> chromosome) {
        for (Entry<Integer, List<Integer>> entry : fixedAssignments.entrySet()) {

            List<Integer> fixedElderlyIdxs = entry.getValue();

            for (long elderlyIdx : fixedElderlyIdxs) {
                if (employeesCapacityLeft[entry.getKey()] > 0) {
                    if (elderlyIdx > -1) {
                        employeesCapacityLeft[entry.getKey()]--;
                        elderlyIndexs.removeIf(elderlyIndicie -> elderlyIndicie == elderlyIdx);
                    }

                } else {
                    throw new IllegalStateException("직원 " + entry.getKey() + "의 capacity가 초과되었습니다.");
                }
            }
            chromosome.set(entry.getKey(), new ArrayList<>(fixedElderlyIdxs));
        }
    }

    public List<List<Integer>> initializeChromosomeWithMaximumCapacity(List<EmployeeDTO> employees, int numEmployees) {
        List<List<Integer>> initializeChromosome = new ArrayList<>();
        for (int e = 0; e < numEmployees; e++) {
            List<Integer> chromosome = new ArrayList<>();
            for (int j = 0; j < employees.get(e).maximumCapacity(); j++) {
                chromosome.add(-1);
            }
            initializeChromosome.add(chromosome);
        }
        return initializeChromosome;
    }

    public List<Integer> createRandomElderlyIndexs(int totalElderly) {
        List<Integer> elderlyIndexs = new ArrayList<>();
        for (int i = 0; i < totalElderly; i++) {
            elderlyIndexs.add(i);
        }
        Collections.shuffle(elderlyIndexs);
        return elderlyIndexs;
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
