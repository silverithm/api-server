package com.silverithm.vehicleplacementsystem.entity;


import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
public class Chromosome implements Serializable {

    private List<List<Integer>> genes;
    private double fitness;
    private List<Double> departureTimes;
    private int totalElderly;

    public Chromosome(List<CoupleRequestDTO> couples, List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                      Map<Integer, List<Integer>> fixedAssignments) throws Exception {

        int numEmployees = employees.size();
        int totalElderly = elderly.size();

        int maximumCapacity = employees.stream().mapToInt(employee -> employee.maximumCapacity()).sum();

        if (maximumCapacity < totalElderly) {
            throw new Exception("[ERROR] 배치 가능 인원을 초과하였습니다.");
        }

        List<Integer> elderlyIndexs = createRandomElderlyIndexs(totalElderly);
        int[] employeesCapacityLeft = initializeEmployeesCapacityLeft(employees);
        List<List<Integer>> chromosome = initializeChromosomeWithMaximumCapacity(employees);
        fixCoupleElderlyAtChromosome(elderly, couples, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixElderlyAtChromosome(fixedAssignments, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixInitialChromosome(employees, employeesCapacityLeft, elderlyIndexs, chromosome);
        fixRandomElderlyIndexAtChromosome(employeesCapacityLeft, elderlyIndexs, chromosome);
        removeEmptyChromosome(chromosome);

        genes = chromosome;

    }

    public int[] initializeEmployeesCapacityLeft(List<EmployeeDTO> employees) {

        int[] employeesCapacityLeft = new int[employees.size()];

        for (int i = 0; i < employees.size(); i++) {
            employeesCapacityLeft[i] = employees.get(i).maximumCapacity();
        }

        return employeesCapacityLeft;
    }

    public void removeEmptyChromosome(List<List<Integer>> chromosome) {
        for (List<Integer> subList : chromosome) {
            Iterator<Integer> it = subList.iterator();
            while (it.hasNext()) {
                if (it.next() == -1) {
                    it.remove();
                }
            }
        }
    }

    public void fixRandomElderlyIndexAtChromosome(int[] employeesCapacityLeft,
                                                  List<Integer> elderlyIndexs,
                                                  List<List<Integer>> chromosome) {
        int startIndex = 0;
        Random rand = new Random();

        while (startIndex < elderlyIndexs.size()) {
            int randIndex = rand.nextInt(employeesCapacityLeft.length);
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

    public void fixInitialChromosome(List<EmployeeDTO> employees, int[] employeesCapacityLeft,
                                     List<Integer> elderlyIndexs, List<List<Integer>> chromosome) {
        for (int i = 0; i < employees.size(); i++) {
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

    private void fixCoupleElderlyAtChromosome(List<ElderlyDTO> elderly, List<CoupleRequestDTO> coupleElderlyList,
                                              int[] employeesCapacityLeft,
                                              List<Integer> elderlyIndexs, List<List<Integer>> chromosome) {
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
                List<Integer> employeeChromosome = chromosome.get(employee);
                List<Integer> availablePositions = new ArrayList<>();

                for (int i = 0; i < employeeChromosome.size() - 1; i++) {
                    if (employeeChromosome.get(i) == -1 && employeeChromosome.get(i + 1) == -1) {
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

                    employeeChromosome.set(position, elderIdx1);
                    employeeChromosome.set(position + 1, elderIdx2);
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

    public List<List<Integer>> initializeChromosomeWithMaximumCapacity(List<EmployeeDTO> employees) {
        List<List<Integer>> initializeChromosome = new ArrayList<>();
        for (int e = 0; e < employees.size(); e++) {
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


}
