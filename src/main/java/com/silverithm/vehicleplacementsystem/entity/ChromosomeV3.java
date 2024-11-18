package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Setter
@NoArgsConstructor
@Slf4j
public class ChromosomeV3 implements Serializable {

    private int[][] genes;
    private double fitness;
    private List<Double> departureTimes;
    private int totalElderly;

    public ChromosomeV3(List<CoupleRequestDTO> couples, List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                        Map<Integer, List<Integer>> fixedAssignments) throws Exception {

        int numEmployees = employees.size();
        int totalElderly = elderly.size();

        int maximumCapacity = employees.stream().mapToInt(employee -> employee.maximumCapacity()).sum();

        if (maximumCapacity < totalElderly) {
            throw new Exception("[ERROR] 배치 가능 인원을 초과하였습니다.");
        }

        List<Integer> elderlyIndexs = createRandomElderlyIndexs(totalElderly);
        int[] employeesCapacityLeft = initializeEmployeesCapacityLeft(employees);
        genes = initializeChromosomeWithMaximumCapacity(employees);
        fixCoupleElderlyAtChromosome(elderly, couples, employeesCapacityLeft, elderlyIndexs);
        fixElderlyAtChromosome(fixedAssignments, employeesCapacityLeft, elderlyIndexs);
        fixInitialChromosome(employees, employeesCapacityLeft, elderlyIndexs);
        fixRandomElderlyIndexAtChromosome(employeesCapacityLeft, elderlyIndexs);
        removeEmptyChromosome();

    }

    public int[] initializeEmployeesCapacityLeft(List<EmployeeDTO> employees) {

        int[] employeesCapacityLeft = new int[employees.size()];

        for (int i = 0; i < employees.size(); i++) {
            employeesCapacityLeft[i] = employees.get(i).maximumCapacity();
        }

        return employeesCapacityLeft;
    }

    public void removeEmptyChromosome() {
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

    public void fixRandomElderlyIndexAtChromosome(int[] employeesCapacityLeft,
                                                  List<Integer> elderlyIndexs) {
        int startIndex = 0;
        Random rand = new Random();

        while (startIndex < elderlyIndexs.size()) {
            int randIndex = rand.nextInt(employeesCapacityLeft.length);
            for (int i = 0; i < genes[randIndex].length; i++) {
                if (genes[randIndex][i] == -1 && employeesCapacityLeft[randIndex] > 0) {
                    genes[randIndex][i] = Integer.valueOf(elderlyIndexs.get(startIndex));
                    employeesCapacityLeft[randIndex]--;
                    startIndex++;
                    break;
                }
            }
        }
    }

    public void fixInitialChromosome(List<EmployeeDTO> employees, int[] employeesCapacityLeft,
                                     List<Integer> elderlyIndexs) {
        for (int i = 0; i < employees.size(); i++) {
            for (int j = 0; j < employees.get(i).maximumCapacity(); j++) {
                if (employeesCapacityLeft[i] > 0 && elderlyIndexs.size() > 0 && genes[i][j] == -1) {
                    genes[i][j] = elderlyIndexs.get(0);
                    employeesCapacityLeft[i]--;
                    elderlyIndexs.remove(0);
                    break;
                }
            }
        }

    }

    public void fixElderlyAtChromosome(Map<Integer, List<Integer>> fixedAssignments, int[] employeesCapacityLeft,
                                       List<Integer> elderlyIndexs) {
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
            genes[entry.getKey()] = fixedElderlyIdxs.stream().mapToInt(i -> i).toArray();

        }
    }

    private void fixCoupleElderlyAtChromosome(List<ElderlyDTO> elderly, List<CoupleRequestDTO> coupleElderlyList,
                                              int[] employeesCapacityLeft,
                                              List<Integer> elderlyIndexs) {
        Random rand = new Random();
        Map<Long, Integer> elderlyIdToIndex = new HashMap<>();
        for (int i = 0; i < elderly.size(); i++) {
            elderlyIdToIndex.put(elderly.get(i).id(), i);
        }

        for (CoupleRequestDTO couple : coupleElderlyList) {
            boolean assigned = false;

            List<Integer> employees = IntStream.range(0, employeesCapacityLeft.length)
                    .boxed()
                    .filter(i -> employeesCapacityLeft[i] >= 2)
                    .collect(Collectors.toList());

            Collections.shuffle(employees, new Random());

            for (int employee : employees) {
                int[] employeeChromosome = genes[employee];
                List<Integer> availablePositions = new ArrayList<>();

                for (int i = 0; i < employeeChromosome.length - 1; i++) {
                    if (employeeChromosome[i] == -1 && employeeChromosome[i + 1] == -1) {
                        availablePositions.add(i);
                    }
                }

                // 자리가 있는지 확인
                if (!availablePositions.isEmpty()) {
                    // 가능한 위치 중 무작위 선택
                    int positionIndex = rand.nextInt(availablePositions.size());
                    int position = availablePositions.get(positionIndex);

                    int elderIdx1 = elderlyIdToIndex.get(couple.elderId1());
                    int elderIdx2 = elderlyIdToIndex.get(couple.elderId2());

                    employeeChromosome[position] = elderIdx1;
                    employeeChromosome[position + 1] = elderIdx2;
                    elderlyIndexs.remove(Integer.valueOf(elderIdx1));
                    elderlyIndexs.remove(Integer.valueOf(elderIdx2));
                    employeesCapacityLeft[employee] -= 2;

                    assigned = true;
                    break;  // 성공적으로 배정했으면 다음 부부로 이동
                }
            }

            if (!assigned) {
                throw new RuntimeException(
                        "No available employees to assign couple or no available positions for this couple.");
            }
        }
    }

    public int[][] initializeChromosomeWithMaximumCapacity(List<EmployeeDTO> employees) {

        int[][] initializeChromosome = new int[employees.size()][];

        for (int e = 0; e < employees.size(); e++) {
            int[] chromosome = new int[employees.get(e).maximumCapacity()];

            for (int j = 0; j < employees.get(e).maximumCapacity(); j++) {
                chromosome[j] = -1;
            }
            initializeChromosome[e] = chromosome;
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

    public static ChromosomeV3 copy(ChromosomeV3 original) {
        ChromosomeV3 copy = new ChromosomeV3();

        // 깊은 복사 수행
        copy.genes = new int[original.genes.length][];
        for (int i = 0; i < original.genes.length; i++) {
            copy.genes[i] = Arrays.copyOf(original.genes[i], original.genes[i].length);
        }

        copy.totalElderly = original.totalElderly;
        copy.fitness = original.fitness;
        copy.departureTimes = original.getDepartureTimes();

        return copy;
    }


}
