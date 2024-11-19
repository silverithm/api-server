package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.entity.ChromosomeV3;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.DistanceScore;
import com.silverithm.vehicleplacementsystem.entity.DurationScore;
import com.silverithm.vehicleplacementsystem.entity.FixedAssignmentsV2;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableCaching
public class GeneticAlgorithmV5 {


    private static final Random rand = new Random();

    private static final int MAX_ITERATIONS = 300;
    private static final int POPULATION_SIZE = 20000;
    private static final double MUTATION_RATE = 0.9;
    private static final double CROSSOVER_RATE = 0.7;
    private static final int BATCH_SIZE = 200;

    private final List<EmployeeDTO> employees;
    private final List<ElderlyDTO> elderlys;
    private final List<CoupleRequestDTO> couples;
    private final FixedAssignmentsV2 fixedAssignments;
    private Map<String, Map<String, Integer>> distanceMatrix;
    private DispatchType dispatchType;
    private String userName;

    private final SSEService sseService;

    public GeneticAlgorithmV5(List<EmployeeDTO> employees,
                              List<ElderlyDTO> elderly,
                              List<CoupleRequestDTO> couples,
                              List<FixedAssignmentsDTO> fixedAssignments,
                              SSEService sseService
    ) {
        this.employees = employees;
        this.elderlys = elderly;
        this.couples = couples;
        this.fixedAssignments = generateFixedAssignmentMap(fixedAssignments, elderlys, employees);
        this.sseService = sseService;
    }

    public void initialize(Map<String, Map<String, Integer>> distanceMatrix, DispatchType dispatchType,
                           String userName) {
        this.distanceMatrix = distanceMatrix;
        this.dispatchType = dispatchType;
        this.userName = userName;
    }


    public List<ChromosomeV3> run() throws Exception {
        // 초기 솔루션 생성
        List<ChromosomeV3> chromosomes;
        try {

            chromosomes = generateInitialPopulation(fixedAssignments);
            sseService.notify(userName, 20);

            for (int i = 0; i < MAX_ITERATIONS; i++) {

                sseService.notify(userName, String.format("%.1f", 20 + ((i / (double) MAX_ITERATIONS) * 60)));

                // 평가
                evaluatePopulation(chromosomes);
                // 선택
                List<ChromosomeV3> selectedChromosomes = selectChromosomes(chromosomes);
                // 교차
                List<ChromosomeV3> offspringChromosomes = crossover(selectedChromosomes);
                // 돌연변이
                List<ChromosomeV3> mutatedChromosomes = mutate(offspringChromosomes);
                // 다음 세대 생성
                chromosomes = combinePopulations(selectedChromosomes, offspringChromosomes, mutatedChromosomes);
//                log.info(chromosomes.get(0).getFitness() + " " + chromosomes.get(0).getGenes());

            }


        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("genetic algorithm run exception : " + e);
        }
        // 반복

        Collections.sort(chromosomes, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));
        // 최적의 솔루션 추출
        return chromosomes;

    }

    private List<ChromosomeV3> selectChromosomes(List<ChromosomeV3> chromosomes) {
        if (chromosomes.size() < POPULATION_SIZE) {
            return chromosomes;
        }

        return chromosomes.subList(0, POPULATION_SIZE);
    }

    private FixedAssignmentsV2 generateFixedAssignmentMap(List<FixedAssignmentsDTO> fixedAssignmentDtos,
                                                          List<ElderlyDTO> elderlys,
                                                          List<EmployeeDTO> employees) {
        FixedAssignmentsV2 fixedAssignments = new FixedAssignmentsV2(fixedAssignmentDtos, employees, elderlys);
        return fixedAssignments;
    }

    private List<ChromosomeV3> generateInitialPopulation(FixedAssignmentsV2 fixedAssignments) throws Exception {

        List<ChromosomeV3> chromosomes = new ArrayList<>();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            chromosomes.add(new ChromosomeV3(couples, employees, elderlys, fixedAssignments.getFixedAssignments()));
        }
        return chromosomes;
    }

    private void evaluatePopulation(List<ChromosomeV3> chromosomes) {
        for (ChromosomeV3 chromosome : chromosomes) {
            chromosome.setFitness(calculateFitness(chromosome));
        }
    }

    public double calculateFitness(ChromosomeV3 chromosome) {
        double fitness = 0.0;

        if (!isValidChromosome(chromosome)) {
            return 0.0;
        }

        fitness += calculateFitnessForDepartureTimes(chromosome);
        fitness += addFitnessForProximity(chromosome);

        return fitness;
    }

    private boolean isValidChromosome(ChromosomeV3 chromosome) {
        // 모든 제약조건 검사
        return evaluateFrontSeatAssignments(chromosome) &&
                evaluateFixedAssignments(chromosome) &&
                evaluateCoupleAssignments(chromosome);
    }

    private boolean evaluateCoupleAssignments(ChromosomeV3 chromosome) {
        // Elderly ID를 인덱스로 매핑하는 맵 생성
        Map<Long, Integer> elderlyIdToIndex = new HashMap<>();
        for (int i = 0; i < elderlys.size(); i++) {
            elderlyIdToIndex.put(elderlys.get(i).id(), i);
        }

        // 각 부부 어르신에 대해 평가
        for (CoupleRequestDTO couple : couples) {
            long elderly1Id = couple.elderId1();
            long elderly2Id = couple.elderId2();

            // 실제 ID를 인덱스로 변환
            Integer elderly1Index = elderlyIdToIndex.get(elderly1Id);
            Integer elderly2Index = elderlyIdToIndex.get(elderly2Id);

            if (elderly1Index == null || elderly2Index == null) {
                // 인덱스 변환 실패시 다음 부부로 건너뛰기
                continue;
            }

            boolean found = false;

            // 부부가 같은 차량에 배정되었는지 확인
            for (int[] gene : chromosome.getGenes()) {
                if (Arrays.stream(gene).boxed().filter(e -> e == elderly1Index).count() >= 1
                        && Arrays.stream(gene).boxed().filter(e -> e == elderly2Index).count() >= 1) {
                    found = true;
                    break;
                }
            }

            // 부부가 다른 차량에 배정된 경우
            if (!found) {
                return false;
            }
        }
        return true; // 모든 부부가 같은 차량에 배정됨
    }

    private double calculateFitnessForDepartureTimes(ChromosomeV3 chromosome) {
        double fitness;
        List<Double> departureTimes = calculateDepartureTimes(chromosome);
        chromosome.setDepartureTimes(departureTimes);
        double totalDepartureTime = departureTimes.stream().mapToDouble(Double::doubleValue).sum();

        if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
            fitness = 10000000 / ((totalDepartureTime + 1.0));
            return fitness;
        }

        fitness = 10000000 / ((totalDepartureTime + 1.0) / 1000);
//        log.info("total departure time : " + totalDepartureTime + ", fitness : " + fitness);
        return fitness;
    }

    private double addFitnessForProximity(ChromosomeV3 chromosome) {

        double fitness = 0.0;

        if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
            for (int i = 0; i < chromosome.getGenes().length; i++) {
                for (int j = 0; j < chromosome.getGenes()[i].length - 1; j++) {
                    int elderlyIndex1 = chromosome.getGenes()[i][j];
                    int elderlyIndex2 = chromosome.getGenes()[i][j + 1];
                    fitness += calculateFitnessForFromAndTo("Elderly_" + elderlys.get(elderlyIndex1).id(),
                            "Elderly_" + elderlys.get(elderlyIndex2).id());
                }
                fitness = addFitnessForDispatchTypes(chromosome, fitness, i);
            }
        }

        if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
            for (int i = 0; i < chromosome.getGenes().length; i++) {
                for (int j = 0; j < chromosome.getGenes()[i].length - 1; j++) {
                    int elderlyIndex1 = chromosome.getGenes()[i][j];
                    int elderlyIndex2 = chromosome.getGenes()[i][j + 1];

                    if (calculateFitnessForFromAndTo("Elderly_" + elderlys.get(elderlyIndex1).id(),
                            "Elderly_" + elderlys.get(elderlyIndex2).id()) == 10000) {
                        fitness += 10000;
                    } else {

                        fitness += calculateFitnessForFromAndTo("Employee_" + employees.get(i).id(),
                                "Elderly_" + elderlys.get(elderlyIndex1).id());
                    }

                }
                fitness = addFitnessForDispatchTypes(chromosome, fitness, i);
            }
        }

        return fitness;
    }

    private boolean evaluateFixedAssignments(ChromosomeV3 chromosome) {
        return fixedAssignments.evaluateFitness(chromosome);
    }

    private boolean evaluateFrontSeatAssignments(ChromosomeV3 chromosome) {
        for (int i = 0; i < employees.size(); i++) {
            boolean frontSeatAssigned = false;
            for (int j = 0; j < chromosome.getGenes()[i].length; j++) {
                if (elderlys.get(j).requiredFrontSeat()) {
                    if (frontSeatAssigned) {
                        return false;
                    }
                    frontSeatAssigned = true;
                }
            }
        }
        return true;
    }

    private double addFitnessForDispatchTypes(ChromosomeV3 chromosome, double fitness, int i) {
        if (dispatchType.equals(DispatchType.DISTANCE_OUT) || dispatchType.equals(DispatchType.DURATION_OUT)) {
            if (employees.get(i).isDriver()) {
                fitness += calculateFitnessForFromAndTo("Elderly_" + elderlys.get(
                        chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id(), "Company");
            }

            if (!employees.get(i).isDriver()) {
                fitness += calculateFitnessForFromAndTo("Elderly_" + elderlys.get(
                                chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id(),
                        "Employee_" + employees.get(i).id());
            }
        }

        if (dispatchType.equals(DispatchType.DURATION_IN) || dispatchType.equals(DispatchType.DISTANCE_IN)) {
            if (employees.get(i).isDriver()) {
                fitness += calculateFitnessForFromAndTo("Company",
                        "Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
            }
            if (!employees.get(i).isDriver()) {
                fitness += calculateFitnessForFromAndTo("Employee_" + employees.get(i).id(),
                        "Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
            }
            fitness += calculateFitnessForFromAndTo(
                    "Elderly_" + elderlys.get(chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1])
                            .id(), "Company");
        }
        return fitness;
    }

    private double calculateFitnessForFromAndTo(String from, String to) {

        double score = 0;

        if (dispatchType == DispatchType.DURATION_OUT || dispatchType == DispatchType.DURATION_IN) {
            score = DurationScore.getScore(distanceMatrix.get(from).get(to));
        }

        if (dispatchType == DispatchType.DISTANCE_OUT || dispatchType == DispatchType.DISTANCE_IN) {
            score = DistanceScore.getScore(distanceMatrix.get(from).get(to));
        }

        return score;
    }

    public List<Double> calculateDepartureTimes(ChromosomeV3 chromosome) {

        List<Double> departureTimes = new ArrayList<>();

        if (dispatchType.equals(DispatchType.DISTANCE_OUT) || dispatchType.equals(DispatchType.DURATION_OUT)) {
            for (int i = 0; i < chromosome.getGenes().length; i++) {
                double departureTime = 0.0;
                for (int j = 0; j < chromosome.getGenes()[i].length - 1; j++) {
                    String company = "Company";

                    String startNodeId = "Elderly_" + elderlys.get(chromosome.getGenes()[i][j]).id();
                    String destinationNodeId = "Elderly_" + elderlys.get(chromosome.getGenes()[i][j + 1]).id();

                    if (j == 0) {
                        departureTime += distanceMatrix.get(company)
                                .get("Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
                    }

                    departureTime += distanceMatrix.get(startNodeId).get(destinationNodeId);
                }

                departureTime += distanceMatrix.get("Elderly_" + elderlys.get(
                                chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id())
                        .get("Employee_" + employees.get(i).id());

                if (employees.get(i).isDriver()) {
                    departureTime += distanceMatrix.get("Elderly_" + elderlys.get(
                                    chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id())
                            .get("Company");
                }

                if (!employees.get(i).isDriver()) {
                    departureTime += distanceMatrix.get("Elderly_" + elderlys.get(
                                    chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id())
                            .get("Employee_" + employees.get(i).id());
                }

                departureTimes.add(departureTime);
            }
        }

        if (dispatchType.equals(DispatchType.DURATION_IN) || dispatchType.equals(DispatchType.DISTANCE_IN)) {
            for (int i = 0; i < chromosome.getGenes().length; i++) {
                String company = "Company";
                double departureTime = 0.0;

                for (int j = 0; j < chromosome.getGenes()[i].length - 1; j++) {
                    String startNodeId = "Elderly_" + elderlys.get(chromosome.getGenes()[i][j]).id();
                    String destinationNodeId = "Elderly_" + elderlys.get(chromosome.getGenes()[i][j + 1]).id();
                    if (j == 0) {
                        departureTime += distanceMatrix.get("Employee_" + employees.get(i).id())
                                .get("Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
                    }

                    departureTime += distanceMatrix.get(startNodeId).get(destinationNodeId);
                }

                if (employees.get(i).isDriver()) {
                    departureTime += distanceMatrix.get("Company")
                            .get("Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
                }

                if (!employees.get(i).isDriver()) {
                    departureTime += distanceMatrix.get("Employee_" + employees.get(i).id())
                            .get("Elderly_" + elderlys.get(chromosome.getGenes()[i][0]).id());
                }

                departureTime += distanceMatrix.get("Elderly_" + elderlys.get(
                        chromosome.getGenes()[i][chromosome.getGenes()[i].length - 1]).id()).get(company);

                departureTimes.add(departureTime);
            }
        }

        return departureTimes;
    }


    private List<ChromosomeV3> crossover(List<ChromosomeV3> selectedChromosomes) {
        List<ChromosomeV3> offspring = new ArrayList<>(selectedChromosomes.size());

        for (int i = 0; i < selectedChromosomes.size(); i += 2) {
            ChromosomeV3 parent1 = ChromosomeV3.copy(selectedChromosomes.get(i));
            ChromosomeV3 parent2 = ChromosomeV3.copy(selectedChromosomes.get(i + 1));
            // Crossover 확률에 따라 진행
            if (rand.nextDouble() < CROSSOVER_RATE) {
                offspring.addAll(multiPointCrossover(parent1, parent2));
                continue;
            }

            if (rand.nextDouble() >= CROSSOVER_RATE) {
                offspring.add(parent1);
                offspring.add(parent2);
                continue;
            }

        }

        return offspring;
    }


    private List<ChromosomeV3> multiPointCrossover(ChromosomeV3 parent1, ChromosomeV3 parent2) {
        int[] crossoverPoints = createSortedRandomCrossoverPoints(parent1);

        ChromosomeV3 child1 = ChromosomeV3.copy(parent1);
        ChromosomeV3 child2 = ChromosomeV3.copy(parent2);

        swapGeneticSegments(parent1, parent2, crossoverPoints, child1, child2);

        fixDuplicateAssignments(child1, elderlys);
        fixDuplicateAssignments(child2, elderlys);

        return Arrays.asList(child1, child2);
    }

    private int[] createSortedRandomCrossoverPoints(ChromosomeV3 parent1) {
        int[] crossoverPoints = new int[2];
        for (int i = 0; i < crossoverPoints.length; i++) {
            crossoverPoints[i] = rand.nextInt(parent1.getGenes().length);
        }
        Arrays.sort(crossoverPoints);
        return crossoverPoints;
    }

    private void swapGeneticSegments(ChromosomeV3 parent1, ChromosomeV3 parent2, int[] crossoverPoints,
                                     ChromosomeV3 child1,
                                     ChromosomeV3 child2) {
        for (int i = 0; i < crossoverPoints.length; i++) {
            int start = i == 0 ? 0 : crossoverPoints[i - 1];
            int end = crossoverPoints[i];
            for (int j = start; j < end; j++) {
                int[] parent1Gene = parent1.getGenes()[j];
                int[] parent2Gene = parent2.getGenes()[j];
                int minLength = Math.min(parent1Gene.length, parent2Gene.length);
                for (int k = 0; k < minLength; k++) {
                    if (i % 2 == 0) {
                        child1.getGenes()[j][k] = parent1Gene[k];
                        child2.getGenes()[j][k] = parent2Gene[k];
                        continue;
                    }
                    if (i % 2 != 0) {
                        child1.getGenes()[j][k] = parent2Gene[k];
                        child2.getGenes()[j][k] = parent1Gene[k];
                        continue;
                    }
                }
            }
        }

    }

    private int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;  // 값을 찾지 못한 경우
    }


    private void fixDuplicateAssignments(ChromosomeV3 child, List<ElderlyDTO> elderlys) {
        int totalElderly = elderlys.size();
        // 1. 빠른 조회를 위한 boolean 배열 사용
        boolean[] used = new boolean[totalElderly];
        // 2. 미사용 어르신 인덱스 추적
        List<Integer> unusedIndices = new ArrayList<>(totalElderly);

        // 첫 번째 패스: 사용된 어르신 체크
        for (int[] gene : child.getGenes()) {
            for (int elderlyId : gene) {
                if (elderlyId >= 0 && elderlyId < totalElderly) {
                    if (used[elderlyId]) {
                        // 중복된 경우 나중에 재할당하기 위해 -1로 마킹
                        gene[indexOf(gene, elderlyId)] = -1;
                    } else {
                        used[elderlyId] = true;
                    }
                }
            }
        }

        // 미사용 어르신 인덱스 수집
        for (int i = 0; i < totalElderly; i++) {
            if (!used[i]) {
                unusedIndices.add(i);
            }
        }

        // 중복 제거된 위치에 미사용 어르신 할당
        int unusedIndex = 0;
        for (int[] gene : child.getGenes()) {
            for (int i = 0; i < gene.length; i++) {
                if (gene[i] == -1) {
                    gene[i] = unusedIndices.get(unusedIndex++);
                }
            }
        }
    }

    private List<ChromosomeV3> mutate(List<ChromosomeV3> offspringChromosomes) throws Exception {
        List<ChromosomeV3> mutatedChromosomes = new ArrayList<>();

        for (ChromosomeV3 chromosome : offspringChromosomes) {
            // 염색체 깊은 복사
            ChromosomeV3 newChromosome = ChromosomeV3.copy(chromosome);

            if (rand.nextDouble() < MUTATION_RATE) {
                int mutationPoint1 = rand.nextInt(newChromosome.getGenes().length);
                int[] employeeAssignment = newChromosome.getGenes()[mutationPoint1];
                int mutationPoint2 = rand.nextInt(employeeAssignment.length);

                int mutationPoint3 = rand.nextInt(newChromosome.getGenes().length);
                int[] employeeAssignment2 = newChromosome.getGenes()[mutationPoint3];
                int mutationPoint4 = rand.nextInt(employeeAssignment2.length);

                // 염색
                int tempElderly = employeeAssignment2[mutationPoint4];

                employeeAssignment2[mutationPoint4] = employeeAssignment[mutationPoint2];
                employeeAssignment[mutationPoint2] = tempElderly;


            }

            mutatedChromosomes.add(newChromosome); // 변이된 염색체를 리스트에 추가
        }

        return mutatedChromosomes; // 변이된 새로운 염색체 리스트 반환
    }

    private List<ChromosomeV3> combinePopulations(List<ChromosomeV3> chromosomes,
                                                  List<ChromosomeV3> offspringChromosomes,
                                                  List<ChromosomeV3> mutatedChromosomes) {
        List<ChromosomeV3> combinedChromosomes = new ArrayList<>(POPULATION_SIZE);
        Set<String> uniqueGenes = new HashSet<>();

        // 1. 모든 염색체를 하나의 스트림으로 처리
        Stream.of(chromosomes, offspringChromosomes, mutatedChromosomes)
                .flatMap(List::stream)
                .filter(c -> c.getFitness() > 0)  // 유효한 해결책만 필터링
                .filter(c -> uniqueGenes.add(c.getGenes().toString()))  // 중복 제거
                .sorted((c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()))  // 적합도 기준 정렬
                .limit(POPULATION_SIZE)  // 상위 N개만 선택
                .forEach(combinedChromosomes::add);

        return combinedChromosomes;
    }


}
