package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.DistanceScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneticAlgorithmService {

    private static final int MAX_ITERATIONS = 300;
    private static final int POPULATION_SIZE = 20000;
    private static final double MUTATION_RATE = 0.9;
    private static final double CROSSOVER_RATE = 0.7;

    private final List<EmployeeDTO> employees;
    private final List<ElderlyDTO> elderlys;
    private final Map<Integer, List<Integer>> fixedAssignmentsMap;
    private final Map<String, Map<String, Integer>> distanceMatrix;
    private final DispatchType dispatchType;


    public GeneticAlgorithmService(List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                                   Map<String, Map<String, Integer>> distanceMatrix,
                                   List<FixedAssignmentsDTO> fixedAssignments, DispatchType dispatchType) {
        this.employees = employees;
        this.elderlys = elderly;
        this.distanceMatrix = distanceMatrix;
        this.fixedAssignmentsMap = generateFixedAssignmentMap(fixedAssignments);
        this.dispatchType = dispatchType;
    }


    public List<Chromosome> run() throws Exception {

        // 초기 솔루션 생성
        List<Chromosome> chromosomes = generateInitialPopulation(fixedAssignmentsMap);

        try {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                // 평가
                evaluatePopulation(chromosomes);
                // 선택
                List<Chromosome> selectedChromosomes = chromosomes;
                // 교차
                List<Chromosome> offspringChromosomes = crossover(selectedChromosomes);
                // 돌연변이
                mutate(offspringChromosomes);
                // 다음 세대 생성
                chromosomes = combinePopulations(selectedChromosomes, offspringChromosomes);

                log.info(chromosomes.get(0).getGenes() + " / " + chromosomes.get(0).getFitness());
            }
        } catch (Exception e) {
            throw new Exception("genetic algorithm run exception : " + e);
        }
        // 반복

        Collections.sort(chromosomes, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));
        // 최적의 솔루션 추출
        return chromosomes;

    }

    private Map<Integer, List<Integer>> generateFixedAssignmentMap(List<FixedAssignmentsDTO> fixedAssignments) {

        Map<Integer, List<Integer>> fixedAssignmentMap = new HashMap<>();
        if (fixedAssignments == null) {
            return fixedAssignmentMap;
        }

        for (FixedAssignmentsDTO fixedAssignment : fixedAssignments) {
            long employeeId = fixedAssignment.employee_id();
            long elderlyId = fixedAssignment.elderly_id();
            int sequence = fixedAssignment.sequence();

            int employee_idx = employees.stream().map((employee) -> employee.id()).collect(Collectors.toList())
                    .indexOf(employeeId);
            int elderly_idx = elderlys.stream().map((elderly) -> elderly.id()).collect(Collectors.toList())
                    .indexOf(elderlyId);

            if (fixedAssignmentMap.get(employee_idx) == null && sequence > 0) {
                List<Integer> createdList = new ArrayList<>();

                for (int i = 0; i < employees.get(employee_idx).maximumCapacity(); i++) {
                    createdList.add(-1);
                }

                createdList.set(sequence - 1, (int) elderly_idx);
                fixedAssignmentMap.put(employee_idx, createdList);
            } else if (sequence > 0) {
                List<Integer> prevList = fixedAssignmentMap.get(employee_idx);
                prevList.set(sequence - 1, (int) elderly_idx);
                fixedAssignmentMap.put(employee_idx, prevList);
            }

//                fixedAssignmentMap.computeIfAbsent(employeeIdx, k -> new ArrayList<>()).add(elderlyIdx);
        }

        return fixedAssignmentMap;
    }

    private List<Chromosome> generateInitialPopulation(Map<Integer, List<Integer>> fixedAssignmentMap) {
        List<Chromosome> chromosomes = new ArrayList<>();
        for (int i = 0; i < POPULATION_SIZE; i++) {
            chromosomes.add(new Chromosome(employees, elderlys, fixedAssignmentMap));

        }
        return chromosomes;
    }

    private void evaluatePopulation(List<Chromosome> chromosomes) {
        for (Chromosome chromosome : chromosomes) {
            chromosome.setFitness(calculateFitness(chromosome));
        }
    }

    private double calculateFitness(Chromosome chromosome) {

        double fitness = 0.0;

        List<Double> departureTimes = calculateDepartureTimes(chromosome);

        chromosome.setDepartureTimes(departureTimes);

        double totalDepartureTime = departureTimes.stream().mapToDouble(Double::doubleValue).sum();

        fitness = 10000000 / (totalDepartureTime + 1.0);

        for (int i = 0; i < chromosome.getGenes().size(); i++) {
            for (int j = 0; j < chromosome.getGenes().get(i).size() - 1; j++) {
                int elderlyIndex1 = chromosome.getGenes().get(i).get(j);
                int elderlyIndex2 = chromosome.getGenes().get(i).get(j + 1);

                fitness += calculateFitnessForElderlyProximity(elderlyIndex1, elderlyIndex2);
            }

            if (dispatchType.equals(DispatchType.OUT)) {

                fitness += calculateFitnessForElderlyAndEmployeeProximity(chromosome, i);

            }
            if (dispatchType.equals((DispatchType.IN))) {

                fitness += DistanceScore.getScore(distanceMatrix.get("Employee_" + employees.get(i).id())
                        .get("Elderly_" + elderlys.get(
                                        chromosome.getGenes().get(i).get(0))
                                .id()));

                fitness += DistanceScore.getScore(distanceMatrix.get("Elderly_" + elderlys.get(
                                chromosome.getGenes().get(i).get(chromosome.getGenes().get(i).size() - 1)).id())
                        .get("Company"));

            }


        }
        // 앞자리에 필수로 타야 하는 노인이 실제로 앞자리에 배정되었는지 확인

        for (int i = 0; i < employees.size(); i++) {
            boolean frontSeatAssigned = false;
            for (int j = 0; j < chromosome.getGenes().get(i).size(); j++) {
                if (elderlys.get(j).requiredFrontSeat()) {
                    if (frontSeatAssigned) {
                        fitness = 0.0;
                        break;
                    }
                    frontSeatAssigned = true;
                }
            }
        }
//        log.info(chromosome.getGenes() + " " + fixedAssignmentsMap.toString());
        for (int employee_idx : fixedAssignmentsMap.keySet()) {
            for (int i = 0; i < chromosome.getGenes().get(employee_idx).size(); i++) {
                if (chromosome.getGenes().get(employee_idx).get(i) != fixedAssignmentsMap.get(employee_idx).get(i)
                        && fixedAssignmentsMap.get(employee_idx).get(i) != -1) {
                    fitness = 0.0;
                    return fitness;
                }
            }

            employee_idx++;
        }

        return fitness;
    }

    private double calculateFitnessForElderlyAndEmployeeProximity(Chromosome chromosome, int i) {
        return DistanceScore.getScore(distanceMatrix.get("Elderly_" + elderlys.get(
                        chromosome.getGenes().get(i).get(chromosome.getGenes().get(i).size() - 1)).id())
                .get("Employee_" + employees.get(i).id()));
    }

    private double calculateFitnessForElderlyProximity(int elderlyIndex1, int elderlyIndex2) {
        return DistanceScore.getScore(distanceMatrix.get("Elderly_" + elderlys.get(elderlyIndex1).id())
                .get("Elderly_" + elderlys.get(elderlyIndex2).id()));
    }

    private List<Double> calculateDepartureTimes(Chromosome chromosome) {
        List<Double> departureTimes = new ArrayList<>();

        if (dispatchType.equals(DispatchType.OUT)) {
            for (int i = 0; i < chromosome.getGenes().size(); i++) {
                double departureTime = 0.0;
                for (int j = 0; j < chromosome.getGenes().get(i).size() - 1; j++) {
                    String company = "Company";
                    String startNodeId = "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j)).id();
                    String destinationNodeId =
                            "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j + 1)).id();

                    if (j == 0) {
                        departureTime += distanceMatrix.get(company)
                                .get("Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(0)).id());
                    }

                    departureTime += distanceMatrix.get(startNodeId).get(destinationNodeId);
                }

                departureTime += distanceMatrix.get(
                                "Elderly_" + elderlys.get(
                                        chromosome.getGenes().get(i).get(chromosome.getGenes().get(i).size() - 1)).id())
                        .get("Employee_" + employees.get(i).id());

                departureTimes.add(departureTime);
            }
        }

        if (dispatchType.equals(DispatchType.IN)) {
            for (int i = 0; i < chromosome.getGenes().size(); i++) {
                String company = "Company";
                double departureTime = 0.0;

                for (int j = 0; j < chromosome.getGenes().get(i).size() - 1; j++) {

                    String startNodeId = "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j)).id();
                    String destinationNodeId =
                            "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j + 1)).id();
                    if (j == 0) {
                        departureTime += distanceMatrix.get("Employee_" + employees.get(i).id())
                                .get("Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(0)).id());
                    }

                    departureTime += distanceMatrix.get(startNodeId).get(destinationNodeId);
                }

                departureTime += distanceMatrix.get(
                                "Elderly_" + elderlys.get(
                                        chromosome.getGenes().get(i).get(chromosome.getGenes().get(i).size() - 1)).id())
                        .get(company);

                departureTimes.add(departureTime);
            }
        }

        return departureTimes;
    }


    private List<Chromosome> crossover(List<Chromosome> selectedChromosomes) {
        Random rand = new Random();
        List<Chromosome> offspring = new ArrayList<>();

        for (int i = 0; i < selectedChromosomes.size(); i += 2) {
            Chromosome parent1 = Chromosome.copy(selectedChromosomes.get(i));
            Chromosome parent2 = Chromosome.copy(selectedChromosomes.get(i + 1));
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


    private List<Chromosome> multiPointCrossover(Chromosome parent1, Chromosome parent2) {
        int[] crossoverPoints = createSortedRandomCrossoverPoints(parent1);

        Chromosome child1 = Chromosome.copy(parent1);
        Chromosome child2 = Chromosome.copy(parent2);

        swapGeneticSegments(parent1, parent2, crossoverPoints, child1, child2);

        fixDuplicateAssignments(child1, elderlys);
        fixDuplicateAssignments(child2, elderlys);

        return Arrays.asList(child1, child2);
    }

    private int[] createSortedRandomCrossoverPoints(Chromosome parent1) {
        Random rand = new Random();
        int[] crossoverPoints = new int[2];
        for (int i = 0; i < crossoverPoints.length; i++) {
            crossoverPoints[i] = rand.nextInt(parent1.getGenes().size());
        }
        Arrays.sort(crossoverPoints);
        return crossoverPoints;
    }

    private void swapGeneticSegments(Chromosome parent1, Chromosome parent2, int[] crossoverPoints, Chromosome child1,
                                     Chromosome child2) {
        for (int i = 0; i < crossoverPoints.length; i++) {
            int start = i == 0 ? 0 : crossoverPoints[i - 1];
            int end = crossoverPoints[i];
            for (int j = start; j < end; j++) {
                List<Integer> parent1Gene = parent1.getGenes().get(j);
                List<Integer> parent2Gene = parent2.getGenes().get(j);
                int minLength = Math.min(parent1Gene.size(), parent2Gene.size());
                for (int k = 0; k < minLength; k++) {
                    if (i % 2 == 0) {
                        child1.getGenes().get(j).set(k, parent1Gene.get(k));
                        child2.getGenes().get(j).set(k, parent2Gene.get(k));
                        continue;
                    }
                    if (i % 2 != 0) {
                        child1.getGenes().get(j).set(k, parent2Gene.get(k));
                        child2.getGenes().get(j).set(k, parent1Gene.get(k));
                        continue;
                    }
                }
            }
        }

    }

    private void fixDuplicateAssignments(Chromosome child, List<ElderlyDTO> elderlys) {
        Set<Integer> assignedElderly = new HashSet<>();
        for (List<Integer> gene : child.getGenes()) {
            for (int i = 0; i < gene.size(); i++) {
                int elderlyId = gene.get(i);
                if (!assignedElderly.add(elderlyId)) {
                    // 중복 발생 시, 다른 노인으로 교체
                    for (int newElderlyId = 0; newElderlyId < elderlys.size(); newElderlyId++) {
                        if (!assignedElderly.contains(newElderlyId)) {
                            gene.set(i, newElderlyId);
                            assignedElderly.add(newElderlyId);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void mutate(List<Chromosome> offspringChromosomes) {

        Random rand = new Random();

        for (Chromosome chromosome : offspringChromosomes) {

            if (rand.nextDouble() < MUTATION_RATE) {

                int mutationPoint1 = rand.nextInt(chromosome.getGenes().size());
                List<Integer> employeeAssignment = chromosome.getGenes().get(mutationPoint1);
                int mutationPoint2 = rand.nextInt(employeeAssignment.size());

                int mutationPoint3 = rand.nextInt(chromosome.getGenes().size());
                List<Integer> employeeAssignment2 = chromosome.getGenes().get(mutationPoint3);
                int mutationPoint4 = rand.nextInt(employeeAssignment2.size());

                int tempElderly = employeeAssignment2.get(mutationPoint4);

                employeeAssignment2.set(mutationPoint4, employeeAssignment.get(mutationPoint2));
                employeeAssignment.set(mutationPoint2, tempElderly);


            }
        }
    }

    private List<Chromosome> combinePopulations(List<Chromosome> chromosomes,
                                                List<Chromosome> offspringChromosomes) {
        List<Chromosome> combinedChromosomes = combineChromosome(
                chromosomes, offspringChromosomes);

        Collections.sort(combinedChromosomes, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));

        return combinedChromosomes.subList(0, POPULATION_SIZE);
    }

    private List<Chromosome> combineChromosome(List<Chromosome> chromosomes, List<Chromosome> offspringChromosomes) {
        List<Chromosome> combinedChromosomes = new ArrayList<>();
        combinedChromosomes.addAll(chromosomes);
        combinedChromosomes.addAll(offspringChromosomes);
        return combinedChromosomes;
    }


}
