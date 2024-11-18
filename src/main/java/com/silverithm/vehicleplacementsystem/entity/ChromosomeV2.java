package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class ChromosomeV2 implements Serializable {
    private static final Random RANDOM = new Random();

    private int[][] genes;            // List<List<Integer>> -> int[][]
    private double fitness;
    private double[] departureTimes;  // List<Double> -> double[]
    private int totalElderly;

    public ChromosomeV2(List<CoupleRequestDTO> couples, List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                        Map<Integer, List<Integer>> fixedAssignments) throws Exception {

        totalElderly = elderly.size();
        int maximumCapacity = calculateMaximumCapacity(employees);

        if (maximumCapacity < totalElderly) {
            throw new Exception("[ERROR] 배치 가능 인원을 초과하였습니다.");
        }

        int[] elderlyIndexes = createRandomElderlyIndexes(totalElderly);
        int[] employeesCapacityLeft = initializeEmployeesCapacityLeft(employees);
        genes = initializeChromosomeWithMaximumCapacity(employees);

        fixCoupleElderlyAtChromosome(elderly, couples, employeesCapacityLeft, elderlyIndexes);
        fixElderlyAtChromosome(fixedAssignments, employeesCapacityLeft, elderlyIndexes);
        fixInitialChromosome(employees, employeesCapacityLeft, elderlyIndexes);
        fixRandomElderlyIndexAtChromosome(employeesCapacityLeft, elderlyIndexes);
        removeEmptySlots();
    }

    private int calculateMaximumCapacity(List<EmployeeDTO> employees) {
        int sum = 0;
        for (EmployeeDTO employee : employees) {
            sum += employee.maximumCapacity();
        }
        return sum;
    }

    private int[] initializeEmployeesCapacityLeft(List<EmployeeDTO> employees) {
        int[] capacities = new int[employees.size()];
        for (int i = 0; i < employees.size(); i++) {
            capacities[i] = employees.get(i).maximumCapacity();
        }
        return capacities;
    }

    private int[] createRandomElderlyIndexes(int totalElderly) {
        int[] indexes = new int[totalElderly];
        for (int i = 0; i < totalElderly; i++) {
            indexes[i] = i;
        }
        // Fisher-Yates 셔플
        for (int i = totalElderly - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            int temp = indexes[i];
            indexes[i] = indexes[j];
            indexes[j] = temp;
        }
        return indexes;
    }

    private int[][] initializeChromosomeWithMaximumCapacity(List<EmployeeDTO> employees) {
        int[][] chromosome = new int[employees.size()][];
        for (int i = 0; i < employees.size(); i++) {
            chromosome[i] = new int[employees.get(i).maximumCapacity()];
            Arrays.fill(chromosome[i], -1);
        }
        return chromosome;
    }

    private void fixCoupleElderlyAtChromosome(List<ElderlyDTO> elderly, List<CoupleRequestDTO> couples,
                                              int[] employeesCapacityLeft, int[] elderlyIndexes) {
        Map<Long, Integer> elderlyIdToIndex = new HashMap<>();
        for (int i = 0; i < elderly.size(); i++) {
            elderlyIdToIndex.put(elderly.get(i).id(), i);
        }

        for (CoupleRequestDTO couple : couples) {
            boolean assigned = false;

            // 가용 직원 배열 생성
            int[] availableEmployees = new int[employeesCapacityLeft.length];
            int availableCount = 0;
            for (int i = 0; i < employeesCapacityLeft.length; i++) {
                if (employeesCapacityLeft[i] >= 2) {
                    availableEmployees[availableCount++] = i;
                }
            }

            // 직원 순서 셔플
            for (int i = availableCount - 1; i > 0; i--) {
                int j = RANDOM.nextInt(i + 1);
                int temp = availableEmployees[i];
                availableEmployees[i] = availableEmployees[j];
                availableEmployees[j] = temp;
            }

            for (int i = 0; i < availableCount && !assigned; i++) {
                int employee = availableEmployees[i];
                int[] employeeGenes = genes[employee];

                for (int j = 0; j < employeeGenes.length - 1; j++) {
                    if (employeeGenes[j] == -1 && employeeGenes[j + 1] == -1) {
                        int elderIdx1 = elderlyIdToIndex.get(couple.elderId1());
                        int elderIdx2 = elderlyIdToIndex.get(couple.elderId2());

                        employeeGenes[j] = elderIdx1;
                        employeeGenes[j + 1] = elderIdx2;
                        removeFromArray(elderlyIndexes, elderIdx1);
                        removeFromArray(elderlyIndexes, elderIdx2);
                        employeesCapacityLeft[employee] -= 2;

                        assigned = true;
                        break;
                    }
                }
            }

            if (!assigned) {
                throw new RuntimeException("부부 배정을 위한 공간을 찾을 수 없습니다.");
            }
        }
    }

    private void fixElderlyAtChromosome(Map<Integer, List<Integer>> fixedAssignments,
                                        int[] employeesCapacityLeft, int[] elderlyIndexes) {
        for (Map.Entry<Integer, List<Integer>> entry : fixedAssignments.entrySet()) {
            int employeeIdx = entry.getKey();
            List<Integer> assignments = entry.getValue();

            if (employeesCapacityLeft[employeeIdx] < assignments.size()) {
                throw new IllegalStateException("직원 " + employeeIdx + "의 capacity가 초과되었습니다.");
            }

            int[] newAssignments = new int[assignments.size()];
            for (int i = 0; i < assignments.size(); i++) {
                newAssignments[i] = assignments.get(i);
                if (newAssignments[i] > -1) {
                    employeesCapacityLeft[employeeIdx]--;
                    removeFromArray(elderlyIndexes, newAssignments[i]);
                }
            }
            genes[employeeIdx] = newAssignments;
        }
    }

    private void fixInitialChromosome(List<EmployeeDTO> employees, int[] employeesCapacityLeft,
                                      int[] elderlyIndexes) {
        int elderlyIndex = 0;
        for (int i = 0; i < employees.size() && elderlyIndex < elderlyIndexes.length; i++) {
            for (int j = 0; j < genes[i].length && elderlyIndex < elderlyIndexes.length; j++) {
                if (employeesCapacityLeft[i] > 0 && genes[i][j] == -1) {
                    genes[i][j] = elderlyIndexes[elderlyIndex++];
                    employeesCapacityLeft[i]--;
                }
            }
        }
    }

    private void fixRandomElderlyIndexAtChromosome(int[] employeesCapacityLeft, int[] elderlyIndexes) {
        int elderlyIndex = 0;
        while (elderlyIndex < elderlyIndexes.length) {
            int employeeIdx = RANDOM.nextInt(employeesCapacityLeft.length);
            if (employeesCapacityLeft[employeeIdx] > 0) {
                for (int j = 0; j < genes[employeeIdx].length; j++) {
                    if (genes[employeeIdx][j] == -1) {
                        genes[employeeIdx][j] = elderlyIndexes[elderlyIndex++];
                        employeesCapacityLeft[employeeIdx]--;
                        break;
                    }
                }
            }
        }
    }

    private void removeEmptySlots() {
        for (int i = 0; i < genes.length; i++) {
            int validCount = 0;
            for (int value : genes[i]) {
                if (value != -1) validCount++;
            }

            if (validCount < genes[i].length) {
                int[] newGenes = new int[validCount];
                int index = 0;
                for (int value : genes[i]) {
                    if (value != -1) {
                        newGenes[index++] = value;
                    }
                }
                genes[i] = newGenes;
            }
        }
    }

    private void removeFromArray(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                array[i] = array[array.length - 1];
                array[array.length - 1] = -1;
                break;
            }
        }
    }

    public static ChromosomeV2 copy(ChromosomeV2 original) {
        ChromosomeV2 copy = new ChromosomeV2();

        // 깊은 복사 수행
        copy.genes = new int[original.genes.length][];
        for (int i = 0; i < original.genes.length; i++) {
            copy.genes[i] = Arrays.copyOf(original.genes[i], original.genes[i].length);
        }

        copy.totalElderly = original.totalElderly;
        copy.fitness = original.fitness;

        if (original.departureTimes != null) {
            copy.departureTimes = Arrays.copyOf(original.departureTimes, original.departureTimes.length);
        }

        return copy;
    }
}