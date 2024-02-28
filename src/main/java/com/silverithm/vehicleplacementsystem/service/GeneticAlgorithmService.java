//package com.silverithm.vehicleplacementsystem.service;
//
//import org.springframework.stereotype.Service;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//
//public class GeneticAlgorithmService {
//
//    // 초기 인구 생성
//    public static List<List<List<Integer>>> initializePopulation(int popSize, int numElderly, int numEmployees) {
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
//    }
//
//    // 적합도 계산
//    public static double evaluateChromosome(List<List<Integer>> chromosome,
//                                            Map<String, Map<String, Integer>> distanceMap) {
//        double totalTime = 0;
//        for (List<Integer> employeeAssignment : chromosome) {
//            totalTime += distanceMap.getOrDefault("Company_" + employeeAssignment.get(0), 0);
//            for (int i = 0; i < employeeAssignment.size() - 1; i++) {
//                totalTime += distanceMap.getOrDefault(employeeAssignment.get(i) + "_" + employeeAssignment.get(i + 1),
//                        0);
//            }
//        }
//        return 1 / totalTime;
//    }
//
//    // 토너먼트 선택
//    public static List<List<List<Integer>>> tournamentSelection(List<List<List<Integer>>> population,
//                                                                Map<String, Map<String, Integer>> distanceMap,
//                                                                int tournamentSize) {
//        List<List<List<Integer>>> selected = new ArrayList<>();
//        Random rand = new Random();
//
//        while (selected.size() < population.size()) {
//            List<List<List<Integer>>> tournament = new ArrayList<>();
//            for (int i = 0; i < tournamentSize; i++) {
//                int randomIndex = rand.nextInt(population.size());
//                tournament.add(population.get(randomIndex));
//            }
//
//            tournament.sort((o1, o2) -> Double.compare(evaluateChromosome(o2, distanceMap),
//                    evaluateChromosome(o1, distanceMap)));
//            selected.add(new ArrayList<>(tournament.get(0)));
//        }
//
//        return selected;
//    }
//
//    // 한 점 교차
//    public static void singlePointCrossover(List<List<List<Integer>>> population, double crossoverRate) {
//        Random rand = new Random();
//        List<List<List<Integer>>> offspring = new ArrayList<>();
//
//        for (int i = 0; i < population.size(); i += 2) {
//            List<List<Integer>> parent1 = population.get(i);
//            List<List<Integer>> parent2 = population.get(i + 1);
//
//            if (rand.nextDouble() < crossoverRate) {
//                int crossoverPoint = rand.nextInt(parent1.size());
//                for (int j = crossoverPoint; j < parent1.size(); j++) {
//                    List<Integer> temp = parent1.get(j);
//                    parent1.set(j, parent2.get(j));
//                    parent2.set(j, temp);
//                }
//            }
//
//            offspring.add(parent1);
//            offspring.add(parent2);
//        }
//
//        population.clear();
//        population.addAll(offspring);
//    }
//
//    // 돌연변이
//    public static void mutate(List<List<List<Integer>>> population, double mutationRate, int numElderly) {
//        Random rand = new Random();
//
//        for (List<List<Integer>> chromosome : population) {
//            if (rand.nextDouble() < mutationRate) {
//                int mutationPoint1 = rand.nextInt(chromosome.size());
//                int mutationPoint2 = rand.nextInt(chromosome.get(mutationPoint1).size());
//                int newElderly = rand.nextInt(numElderly);
//                chromosome.get(mutationPoint1).set(mutationPoint2, newElderly);
//            }
//        }
//    }
//
//    // 유전 알고리즘 실행
//    public static void runGeneticAlgorithm(int popSize, int numElderly, int numEmployees,
//                                           Map<String, Map<String, Integer>> distanceMap, int tournamentSize,
//                                           double crossoverRate,
//                                           double mutationRate, int numGenerations) {
//        List<List<List<Integer>>> population = initializePopulation(popSize, numElderly, numEmployees);
//
//        for (int i = 0; i < numGenerations; i++) {
//            List<List<List<Integer>>> selected = tournamentSelection(population, distanceMap, tournamentSize);
//            singlePointCrossover(selected, crossoverRate);
//            mutate(selected, mutationRate, numElderly);
//            population = selected;
//        }
//
//        // 결과 출력 또는 추가 처리
//        List<List<Integer>> bestSolution = population.get(0);
//        double bestFitness = evaluateChromosome(bestSolution, distanceMap);
//        for (List<List<Integer>> chromosome : population) {
//            double fitness = evaluateChromosome(chromosome, distanceMap);
//            if (fitness > bestFitness) {
//                bestFitness = fitness;
//                bestSolution = chromosome;
//            }
//        }
//
//        System.out.println("Best solution found:");
//        System.out.println(bestSolution);
//        System.out.println("With fitness:");
//        System.out.println(bestFitness);
//    }
//}
