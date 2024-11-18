package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.entity.ChromosomeV2;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.DistanceScore;
import com.silverithm.vehicleplacementsystem.entity.DurationScore;
import com.silverithm.vehicleplacementsystem.entity.FixedAssignments;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableCaching
public class GeneticAlgorithmV2 {
    private static final Random RANDOM = new Random();

    private static final int MAX_ITERATIONS = 300;
    private static final int POPULATION_SIZE = 20000;
    private static final double MUTATION_RATE = 0.9;
    private static final double CROSSOVER_RATE = 0.7;
    private static final int BATCH_SIZE = 200;

    private final EmployeeDTO[] employees;
    private final ElderlyDTO[] elderlys;
    private final CoupleRequestDTO[] couples;
    private final FixedAssignments fixedAssignments;
    private Map<String, Map<String, Integer>> distanceMatrix;
    private DispatchType dispatchType;
    private String userName;
    private final SSEService sseService;

    public GeneticAlgorithmV2(List<EmployeeDTO> employees,
                              List<ElderlyDTO> elderly,
                              List<CoupleRequestDTO> couples,
                              List<FixedAssignmentsDTO> fixedAssignments,
                              SSEService sseService) {
        this.employees = employees.toArray(new EmployeeDTO[0]);
        this.elderlys = elderly.toArray(new ElderlyDTO[0]);
        this.couples = couples.toArray(new CoupleRequestDTO[0]);
        this.fixedAssignments = generateFixedAssignmentMap(fixedAssignments, elderly, employees);
        this.sseService = sseService;
    }

    public void initialize(Map<String, Map<String, Integer>> distanceMatrix,
                           DispatchType dispatchType,
                           String userName) {
        this.distanceMatrix = distanceMatrix;
        this.dispatchType = dispatchType;
        this.userName = userName;
    }

    private FixedAssignments generateFixedAssignmentMap(List<FixedAssignmentsDTO> fixedAssignmentDtos,
                                                        List<ElderlyDTO> elderlys,
                                                        List<EmployeeDTO> employees) {
        return new FixedAssignments(fixedAssignmentDtos, employees, elderlys);
    }

    public ChromosomeV2[] run() throws Exception {
        try {
            ChromosomeV2[] chromosomes = generateInitialPopulation();
            sseService.notify(userName, 20);

            for (int i = 0; i < MAX_ITERATIONS; i++) {
                sseService.notify(userName, String.format("%.1f", 20 + ((i / (double) MAX_ITERATIONS) * 60)));

                evaluatePopulation(chromosomes);
                ChromosomeV2[] selectedChromosomes = selectChromosomes(chromosomes);
                ChromosomeV2[] offspringChromosomes = crossover(selectedChromosomes);
                ChromosomeV2[] mutatedChromosomes = mutate(offspringChromosomes);
                chromosomes = combinePopulations(selectedChromosomes, offspringChromosomes, mutatedChromosomes);
            }

            Arrays.sort(chromosomes, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));
            return chromosomes;

        } catch (Exception e) {
            log.error("Genetic algorithm error", e);
            throw new Exception("genetic algorithm run exception: " + e.getMessage());
        }
    }

    private ChromosomeV2[] generateInitialPopulation() throws Exception {
        ChromosomeV2[] chromosomes = new ChromosomeV2[POPULATION_SIZE];
        for (int i = 0; i < POPULATION_SIZE; i++) {
            chromosomes[i] = new ChromosomeV2(List.of(couples), List.of(employees), List.of(elderlys), fixedAssignments.getFixedAssignments());
        }
        return chromosomes;
    }

    private void evaluatePopulation(ChromosomeV2[] chromosomes) {
        for (ChromosomeV2 chromosome : chromosomes) {
            chromosome.setFitness(calculateFitness(chromosome));
        }
    }

    private ChromosomeV2[] selectChromosomes(ChromosomeV2[] chromosomes) {
        if (chromosomes.length <= POPULATION_SIZE) {
            return chromosomes;
        }
        return Arrays.copyOf(chromosomes, POPULATION_SIZE);
    }
    private double calculateFitness(ChromosomeV2 chromosome) {
        if (!isValidChromosome(chromosome)) {
            return 0.0;
        }

        double fitnessForDepartureTimes = calculateFitnessForDepartureTimes(chromosome);
        double fitnessForProximity = addFitnessForProximity(chromosome);

        return fitnessForDepartureTimes + fitnessForProximity;
    }

    private boolean isValidChromosome(ChromosomeV2 chromosome) {
        return evaluateFrontSeatAssignments(chromosome) &&
                evaluateFixedAssignments(chromosome) &&
                evaluateCoupleAssignments(chromosome);
    }

    private double calculateFitnessForDepartureTimes(ChromosomeV2 chromosome) {
        double[] departureTimes = calculateDepartureTimes(chromosome);
        chromosome.setDepartureTimes(departureTimes);
        double totalDepartureTime = Arrays.stream(departureTimes).sum();

        if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
            return 10000000 / (totalDepartureTime + 1.0);
        }

        return 10000000 / ((totalDepartureTime + 1.0) / 1000);
    }

    private double[] calculateDepartureTimes(ChromosomeV2 chromosome) {
        double[] departureTimes = new double[chromosome.getGenes().length];

        if (dispatchType == DispatchType.DISTANCE_OUT || dispatchType == DispatchType.DURATION_OUT) {
            calculateDepartureTimesForOutbound(chromosome, departureTimes);
        } else {
            calculateDepartureTimesForInbound(chromosome, departureTimes);
        }

        return departureTimes;
    }

    private void calculateDepartureTimesForOutbound(ChromosomeV2 chromosome, double[] departureTimes) {
        int[][] genes = chromosome.getGenes();
        for (int i = 0; i < genes.length; i++) {
            double time = 0.0;
            int[] employeeGenes = genes[i];

            // 회사에서 첫 노인까지
            if (employeeGenes.length > 0) {
                time += getDistance("Company", "Elderly_" + elderlys[employeeGenes[0]].id());
            }

            // 노인 간 이동
            for (int j = 0; j < employeeGenes.length - 1; j++) {
                time += getDistance(
                        "Elderly_" + elderlys[employeeGenes[j]].id(),
                        "Elderly_" + elderlys[employeeGenes[j + 1]].id()
                );
            }

            // 마지막 노인에서 목적지까지
            if (employeeGenes.length > 0) {
                String lastElderlyId = "Elderly_" + elderlys[employeeGenes[employeeGenes.length - 1]].id();
                String destination = employees[i].isDriver() ? "Company" : "Employee_" + employees[i].id();
                time += getDistance(lastElderlyId, destination);
            }

            departureTimes[i] = time;
        }
    }

    private void calculateDepartureTimesForInbound(ChromosomeV2 chromosome, double[] departureTimes) {
        int[][] genes = chromosome.getGenes();
        for (int i = 0; i < genes.length; i++) {
            double time = 0.0;
            int[] employeeGenes = genes[i];

            if (employeeGenes.length > 0) {
                // 시작점에서 첫 노인까지
                String startPoint = employees[i].isDriver() ? "Company" : "Employee_" + employees[i].id();
                time += getDistance(startPoint, "Elderly_" + elderlys[employeeGenes[0]].id());

                // 노인 간 이동
                for (int j = 0; j < employeeGenes.length - 1; j++) {
                    time += getDistance(
                            "Elderly_" + elderlys[employeeGenes[j]].id(),
                            "Elderly_" + elderlys[employeeGenes[j + 1]].id()
                    );
                }

                // 마지막 노인에서 회사까지
                time += getDistance(
                        "Elderly_" + elderlys[employeeGenes[employeeGenes.length - 1]].id(),
                        "Company"
                );
            }

            departureTimes[i] = time;
        }
    }

    private int getDistance(String from, String to) {
        return distanceMatrix.get(from).get(to);
    }

    private double addFitnessForProximity(ChromosomeV2 chromosome) {
        double fitness = 0.0;
        int[][] genes = chromosome.getGenes();

        if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
            for (int i = 0; i < genes.length; i++) {
                fitness += calculateDurationBasedProximity(genes[i], i);
            }
        } else {
            for (int i = 0; i < genes.length; i++) {
                fitness += calculateDistanceBasedProximity(genes[i], i);
            }
        }

        return fitness;
    }

    private double calculateDurationBasedProximity(int[] employeeGenes, int employeeIndex) {
        double fitness = 0.0;

        for (int j = 0; j < employeeGenes.length - 1; j++) {
            String from = "Elderly_" + elderlys[employeeGenes[j]].id();
            String to = "Elderly_" + elderlys[employeeGenes[j + 1]].id();
            fitness += DurationScore.getScore(getDistance(from, to));
        }

        if (employeeGenes.length > 0) {
            // 마지막 노인에서의 처리
            String lastElderlyId = "Elderly_" + elderlys[employeeGenes[employeeGenes.length - 1]].id();
            String destination = employees[employeeIndex].isDriver() ? "Company" :
                    "Employee_" + employees[employeeIndex].id();
            fitness += DurationScore.getScore(getDistance(lastElderlyId, destination));
        }

        return fitness;
    }

    private double calculateDistanceBasedProximity(int[] employeeGenes, int employeeIndex) {
        double fitness = 0.0;

        if (employeeGenes.length > 0) {
            // 첫 노인까지의 거리
            String from = "Employee_" + employees[employeeIndex].id();
            String to = "Elderly_" + elderlys[employeeGenes[0]].id();
            fitness += DistanceScore.getScore(getDistance(from, to));

            // 노인 간 거리
            for (int j = 0; j < employeeGenes.length - 1; j++) {
                from = "Elderly_" + elderlys[employeeGenes[j]].id();
                to = "Elderly_" + elderlys[employeeGenes[j + 1]].id();
                fitness += DistanceScore.getScore(getDistance(from, to));
            }

            // 마지막 노인에서 목적지까지
            String lastElderlyId = "Elderly_" + elderlys[employeeGenes[employeeGenes.length - 1]].id();
            String destination = employees[employeeIndex].isDriver() ? "Company" :
                    "Employee_" + employees[employeeIndex].id();
            fitness += DistanceScore.getScore(getDistance(lastElderlyId, destination));
        }

        return fitness;
    }

    private boolean evaluateCoupleAssignments(ChromosomeV2 chromosome) {
        int[] elderlyToEmployee = new int[elderlys.length];
        Arrays.fill(elderlyToEmployee, -1);

        // 각 elderly가 어느 employee에게 배정되었는지 기록
        for (int i = 0; i < chromosome.getGenes().length; i++) {
            for (int elderlyIdx : chromosome.getGenes()[i]) {
                if (elderlyIdx >= 0) {
                    elderlyToEmployee[elderlyIdx] = i;
                }
            }
        }

        // 부부가 같은 직원에게 배정되었는지 확인
        for (CoupleRequestDTO couple : couples) {
            int elder1Idx = findElderlyIndex(couple.elderId1());
            int elder2Idx = findElderlyIndex(couple.elderId2());

            if (elder1Idx == -1 || elder2Idx == -1) continue;

            if (elderlyToEmployee[elder1Idx] != elderlyToEmployee[elder2Idx] ||
                    !areAdjacent(chromosome.getGenes()[elderlyToEmployee[elder1Idx]], elder1Idx, elder2Idx)) {
                return false;
            }
        }
        return true;
    }

    private boolean areAdjacent(int[] employeeGenes, int elder1Idx, int elder2Idx) {
        for (int i = 0; i < employeeGenes.length - 1; i++) {
            if ((employeeGenes[i] == elder1Idx && employeeGenes[i + 1] == elder2Idx) ||
                    (employeeGenes[i] == elder2Idx && employeeGenes[i + 1] == elder1Idx)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateFixedAssignments(ChromosomeV2 chromosome) {
        Map<Integer, List<Integer>> fixedAssignmentsMap = fixedAssignments.getFixedAssignments();
        int[][] genes = chromosome.getGenes();

        for (Entry<Integer, List<Integer>> entry : fixedAssignmentsMap.entrySet()) {
            int employeeIdx = entry.getKey();
            List<Integer> requiredAssignments = entry.getValue();

            if (employeeIdx >= genes.length) {
                return false;
            }

            int[] actualAssignments = genes[employeeIdx];
            if (!containsAllAssignments(actualAssignments, requiredAssignments)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAllAssignments(int[] actual, List<Integer> required) {
        Set<Integer> actualSet = new HashSet<>();
        for (int value : actual) {
            actualSet.add(value);
        }

        for (int value : required) {
            if (value != -1 && !actualSet.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateFrontSeatAssignments(ChromosomeV2 chromosome) {
        int[][] genes = chromosome.getGenes();
        for (int i = 0; i < genes.length; i++) {
            boolean frontSeatAssigned = false;
            for (int elderlyIdx : genes[i]) {
                if (elderlyIdx >= 0 && elderlys[elderlyIdx].requiredFrontSeat()) {
                    if (frontSeatAssigned) {
                        return false;  // 이미 앞자리가 할당된 상태
                    }
                    frontSeatAssigned = true;
                }
            }
        }
        return true;
    }

    private ChromosomeV2[] crossover(ChromosomeV2[] selectedChromosomes) {
        ChromosomeV2[] offspring = new ChromosomeV2[selectedChromosomes.length];

        for (int i = 0; i < selectedChromosomes.length - 1; i += 2) {
            ChromosomeV2 parent1 = ChromosomeV2.copy(selectedChromosomes[i]);
            ChromosomeV2 parent2 = ChromosomeV2.copy(selectedChromosomes[i + 1]);

            if (RANDOM.nextDouble() < CROSSOVER_RATE) {
                ChromosomeV2[] children = multiPointCrossover(parent1, parent2);
                offspring[i] = children[0];
                offspring[i + 1] = children[1];
            } else {
                offspring[i] = parent1;
                offspring[i + 1] = parent2;
            }
        }

        // 홀수 개일 경우 마지막 염색체 처리
        if (selectedChromosomes.length % 2 != 0) {
            offspring[selectedChromosomes.length - 1] =
                    ChromosomeV2.copy(selectedChromosomes[selectedChromosomes.length - 1]);
        }

        return offspring;
    }

    private ChromosomeV2[] multiPointCrossover(ChromosomeV2 parent1, ChromosomeV2 parent2) {
        // 교차점 생성
        int[] crossoverPoints = new int[2];
        crossoverPoints[0] = RANDOM.nextInt(parent1.getGenes().length);
        do {
            crossoverPoints[1] = RANDOM.nextInt(parent1.getGenes().length);
        } while (crossoverPoints[1] == crossoverPoints[0]);
        Arrays.sort(crossoverPoints);

        ChromosomeV2 child1 = ChromosomeV2.copy(parent1);
        ChromosomeV2 child2 = ChromosomeV2.copy(parent2);

        // 교차점 사이의 유전자 교환
        swapGenesBetweenPoints(child1, child2, crossoverPoints[0], crossoverPoints[1]);

        // 유효성 검사 및 수정
        fixDuplicateAssignments(child1);
        fixDuplicateAssignments(child2);

        return new ChromosomeV2[]{child1, child2};
    }

    private void swapGenesBetweenPoints(ChromosomeV2 child1, ChromosomeV2 child2, int start, int end) {
        int[][] genes1 = child1.getGenes();
        int[][] genes2 = child2.getGenes();

        for (int i = start; i <= end; i++) {
            int[] temp = genes1[i].clone();
            genes1[i] = genes2[i].clone();
            genes2[i] = temp;
        }
    }

    private void fixDuplicateAssignments(ChromosomeV2 child) {
        boolean[] used = new boolean[elderlys.length];
        List<Integer> unusedElders = new ArrayList<>();
        List<int[]> locationsToFix = new ArrayList<>();

        // 사용된 elderly 체크 및 중복/누락 찾기
        for (int i = 0; i < child.getGenes().length; i++) {
            for (int j = 0; j < child.getGenes()[i].length; j++) {
                int elderlyIdx = child.getGenes()[i][j];
                if (elderlyIdx >= 0) {
                    if (!used[elderlyIdx]) {
                        used[elderlyIdx] = true;
                    } else {
                        // 중복된 위치 기록
                        locationsToFix.add(new int[]{i, j});
                    }
                }
            }
        }

        // 미사용 elderly 수집
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                unusedElders.add(i);
            }
        }

        // 중복 위치에 미사용 elderly 할당
        for (int i = 0; i < locationsToFix.size() && i < unusedElders.size(); i++) {
            int[] location = locationsToFix.get(i);
            child.getGenes()[location[0]][location[1]] = unusedElders.get(i);
        }
    }

    private ChromosomeV2[] mutate(ChromosomeV2[] chromosomes) {
        ChromosomeV2[] mutated = new ChromosomeV2[chromosomes.length];

        for (int i = 0; i < chromosomes.length; i++) {
            mutated[i] = ChromosomeV2.copy(chromosomes[i]);

            if (RANDOM.nextDouble() < MUTATION_RATE) {
                // 두 위치 선택 및 교환
                int[][] genes = mutated[i].getGenes();
                int emp1 = RANDOM.nextInt(genes.length);
                int emp2 = RANDOM.nextInt(genes.length);

                if (genes[emp1].length > 0 && genes[emp2].length > 0) {
                    int pos1 = RANDOM.nextInt(genes[emp1].length);
                    int pos2 = RANDOM.nextInt(genes[emp2].length);

                    // 유전자 교환
                    int temp = genes[emp1][pos1];
                    genes[emp1][pos1] = genes[emp2][pos2];
                    genes[emp2][pos2] = temp;

                    // 부부 제약조건 유지를 위한 추가 처리
                    maintainCoupleConstraints(mutated[i], emp1, emp2);
                }
            }
        }

        return mutated;
    }

    private boolean isAdjacent(int[] genes, int elder1, int elder2) {
        // 배열이 너무 작으면 인접할 수 없음
        if (genes.length < 2) {
            return false;
        }

        // 연속된 두 위치 확인
        for (int i = 0; i < genes.length - 1; i++) {
            // 순서 상관없이 둘이 붙어있는지 확인
            if ((genes[i] == elder1 && genes[i + 1] == elder2) ||
                    (genes[i] == elder2 && genes[i + 1] == elder1)) {
                return true;
            }
        }

        return false;
    }

    private void maintainCoupleConstraints(ChromosomeV2 chromosome, int emp1, int emp2) {
        int[][] genes = chromosome.getGenes();
        Map<Integer, Integer> coupleMap = buildCoupleMap();

        for (int i = 0; i < genes[emp1].length - 1; i++) {
            int elder1 = genes[emp1][i];
            if (coupleMap.containsKey(elder1)) {
                int elder2 = coupleMap.get(elder1);
                if (!isAdjacent(genes[emp1], elder1, elder2)) {
                    // 부부가 분리되었다면 재결합
                    swapToMaintainCouple(genes, emp1, emp2, elder1, elder2);
                }
            }
        }
    }

    private Map<Integer, Integer> buildCoupleMap() {
        Map<Integer, Integer> coupleMap = new HashMap<>();
        for (CoupleRequestDTO couple : couples) {
            int elder1Idx = findElderlyIndex(couple.elderId1());
            int elder2Idx = findElderlyIndex(couple.elderId2());
            if (elder1Idx != -1 && elder2Idx != -1) {
                coupleMap.put(elder1Idx, elder2Idx);
                coupleMap.put(elder2Idx, elder1Idx);
            }
        }
        return coupleMap;
    }

    private void swapToMaintainCouple(int[][] genes, int emp1, int emp2, int elder1, int elder2) {
        // 부부 중 한 명을 찾아서 다른 한 명 옆으로 이동
        for (int i = 0; i < genes[emp2].length; i++) {
            if (genes[emp2][i] == elder2) {
                // 적절한 위치 찾아서 교환
                for (int j = 0; j < genes[emp1].length - 1; j++) {
                    if (genes[emp1][j] == elder1 && genes[emp1][j + 1] == -1) {
                        genes[emp1][j + 1] = elder2;
                        genes[emp2][i] = -1;
                        break;
                    }
                }
                break;
            }
        }
    }

    private ChromosomeV2[] combinePopulations(ChromosomeV2[] selected, ChromosomeV2[] offspring, ChromosomeV2[] mutated) {
        // 모든 염색체를 하나의 배열로 합치기
        int totalLength = selected.length + offspring.length + mutated.length;
        ChromosomeV2[] combined = new ChromosomeV2[totalLength];
        System.arraycopy(selected, 0, combined, 0, selected.length);
        System.arraycopy(offspring, 0, combined, selected.length, offspring.length);
        System.arraycopy(mutated, 0, combined, selected.length + offspring.length, mutated.length);

        // 적합도 기준으로 정렬
        Arrays.sort(combined, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));

        // 중복 제거하면서 상위 POPULATION_SIZE개 선택
        Set<String> uniqueGenes = new HashSet<>();
        ChromosomeV2[] result = new ChromosomeV2[POPULATION_SIZE];
        int resultIndex = 0;

        for (ChromosomeV2 chromosome : combined) {
            if (chromosome.getFitness() > 0) {
                String geneString = Arrays.deepToString(chromosome.getGenes());
                if (uniqueGenes.add(geneString)) {
                    result[resultIndex++] = chromosome;
                    if (resultIndex == POPULATION_SIZE) {
                        break;
                    }
                }
            }
        }

        // 부족한 경우 나머지 채우기
        if (resultIndex < POPULATION_SIZE) {
            for (int i = resultIndex; i < POPULATION_SIZE; i++) {
                try {
                    result[i] = new ChromosomeV2(List.of(couples), List.of(employees), List.of(elderlys),
                            fixedAssignments.getFixedAssignments());
                } catch (Exception e) {
                    log.error("Error creating new chromosome", e);
                }
            }
        }

        return result;
    }

    private int findElderlyIndex(long elderlyId) {
        for (int i = 0; i < elderlys.length; i++) {
            if (elderlys[i].id() == elderlyId) {
                return i;
            }
        }
        return -1;
    }
}