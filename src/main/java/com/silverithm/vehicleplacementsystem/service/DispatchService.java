package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CompanyDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class DispatchService {

    //가까운 거리일수록 가중치가 더 들어가야함 - 가까운 거리를 붙여주어야함
    //고정 시키는 인원이 주어지면 해당 직원에게 고정 인원이 없으면 점수를 0으로 해주어야함

    private static int MAX_ITERATIONS = 100;
    private static int POPULATION_SIZE = 1000;
    private static double MUTATION_RATE = 0.005;
    @Autowired
    private LinkDistanceRepository linkDistanceRepository;

    private String key;

    public DispatchService(@Value("${tmap.key}") String key) {
        this.key = key;
    }

    public int callTMapAPI(Location startAddress,
                           Location destAddress) {

        String url = "https://apis.openapi.sk.com/tmap/routes?version=1";

        // 요청 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("Content-Type", "application/json");
        headers.set("appKey", key);

        // 요청 데이터 설정
        String requestBody = String.format(
                "{\"roadType\":32, \"startX\":%.8f, \"startY\":%.8f, \"endX\":%.8f, \"endY\":%.8f}",
                startAddress.getLongitude(), startAddress.getLatitude(), destAddress.getLongitude(),
                destAddress.getLatitude());

        // HTTP 요청 엔티티 생성
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        // RestTemplate 생성
        RestTemplate restTemplate = new RestTemplate();

        // API 호출
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
        );

        int totalTime = Integer.parseInt(responseEntity.getBody().split("\"totalTime\":")[1].split(",")[0].trim());
        // API 응답 출력
        System.out.println("Response status code: " + responseEntity.getStatusCode());
        System.out.println("Response body: " + responseEntity.getBody());
        System.out.println("totalTime " + totalTime);

        return totalTime;


    }

    public List<EmployeeDTO> getOptimizedAssignments(RequestDispatchDTO requestDispatchDTO) {

        List<EmployeeDTO> employees = requestDispatchDTO.employees();
        List<ElderlyDTO> elderlys = requestDispatchDTO.elderlys();
        CompanyDTO company = requestDispatchDTO.company();

        // 거리 행렬 계산
        Map<String, Map<String, Integer>> distanceMatrix = calculateDistanceMatrix(employees, elderlys, company);

        // 유전 알고리즘 실행
        GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(employees, elderlys, distanceMatrix);
        List<Chromosome> chromosomes = geneticAlgorithm.run();

        // 최적의 솔루션 추출
        Chromosome bestChromosome = chromosomes.get(0);

        System.out.println(bestChromosome.getDepartureTimes());

//        for (int i = 0; i < bestChromosome.getGenes().size(); i++) {
//            System.out.print(bestChromosome.getGenes().get(i) + 1 + " ");
//        }

        for (int i = 0; i < bestChromosome.getGenes().size(); i++) {
            for (int j = 0; j < bestChromosome.getGenes().get(i).size(); j++) {
                System.out.print(bestChromosome.getGenes().get(i).get(j) + 1 + " ");
            }
        }

        System.out.println();
        System.out.println();
        System.out.println("- - - - - - - - - - - - - - - - - - - - ");
        System.out.println();
        System.out.println();
        System.out.println("배차 정보");
        System.out.println();

//        int chromosomeIndex = 0;
//        for (int i = 0; i < employees.size(); i++) {
//
//            System.out.print(employees.get(i).name() + " : ");
//            for (int j = 0; j < employees.get(i).maximumCapacity(); j++) {
//                System.out.print(elderlys.get(bestChromosome.getGene(chromosomeIndex)).name() + " , ");
//
//                chromosomeIndex++;
//                if (chromosomeIndex >= bestChromosome.getGeneLength()) {
//                    return employees;
//                }
//            }
//            System.out.println();
//        }

        for (int i = 0; i < employees.size(); i++) {
            System.out.print(employees.get(i).name() + " : ");
            for (int j = 0; j < bestChromosome.getGenes().get(i).size(); j++) {
                System.out.print(elderlys.get(bestChromosome.getGenes().get(i).get(j)).name() + " , ");
            }
            System.out.println();
        }

        return employees;
    }

    private Map<String, Map<String, Integer>> calculateDistanceMatrix(List<EmployeeDTO> employees,
                                                                      List<ElderlyDTO> elderlys,
                                                                      CompanyDTO company) {
        Map<String, Map<String, Integer>> distanceMatrix = new HashMap<>();

        //사용전
        //처음에 디비에서 모든 정보를 가져와서 1차로 매트릭스를 만듬
        //모든 노드들을 돌면서 비어있는 정보가 있으면 calltmapapi를 호출해서 매트릭스를 채워주고 디비에 값을 저장함

        //사용시
        //geneticAlgorithm에 company, employee, elderly가 있다.
        //만들어져있는 calltmapapi를 그냥 위 값들을 이용하여 map에서 꺼내쓰면 된다.

        distanceMatrix.put("Company", new HashMap<>());

        for (EmployeeDTO employee : employees) {
            distanceMatrix.put("Employee_" + employee.id(), new HashMap<>());
        }

        for (ElderlyDTO elderly : elderlys) {
            distanceMatrix.put("Elderly_" + elderly.id(), new HashMap<>());
        }

        for (int i = 0; i < elderlys.size(); i++) {

            String startNodeId = "Company";
            String destinationNodeId = "Elderly_" + elderlys.get(i).id();

            Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(startNodeId,
                    destinationNodeId);
            Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정

            if (totalTime.isPresent()) {
                distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
            }

            if (totalTime.isEmpty()) {
                int callTotalTime = callTMapAPI(company.companyAddress(), elderlys.get(i).homeAddress());
                distanceMatrix.get(startNodeId).put(destinationNodeId, callTotalTime);
                distanceMatrix.get(destinationNodeId).put(startNodeId, callTotalTime);
                linkDistanceRepository.save(new LinkDistance(startNodeId, destinationNodeId, callTotalTime));
                linkDistanceRepository.save(new LinkDistance(destinationNodeId, startNodeId, callTotalTime));
            }

        }

        for (int i = 0; i < elderlys.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {
                String startNodeId = "Elderly_" + elderlys.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(startNodeId,
                        destinationNodeId);
                Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정

                if (totalTime.isPresent()) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
                }

                if (totalTime.isEmpty()) {
                    int callTotalTime = callTMapAPI(elderlys.get(i).homeAddress(), elderlys.get(j).homeAddress());
                    distanceMatrix.get(startNodeId).put(destinationNodeId, callTotalTime);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, callTotalTime);
                    linkDistanceRepository.save(new LinkDistance(startNodeId, destinationNodeId, callTotalTime));
                    linkDistanceRepository.save(new LinkDistance(destinationNodeId, startNodeId, callTotalTime));
                }
            }

        }

        for (int i = 0; i < employees.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {
                String startNodeId = "Employee_" + employees.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(startNodeId,
                        destinationNodeId);
                Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정

                if (totalTime.isPresent()) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
                }

                if (totalTime.isEmpty()) {
                    int callTotalTime = callTMapAPI(employees.get(i).homeAddress(),
                            elderlys.get(j).homeAddress());
                    distanceMatrix.get(startNodeId).put(destinationNodeId, callTotalTime);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, callTotalTime);
                    linkDistanceRepository.save(new LinkDistance(startNodeId, destinationNodeId, callTotalTime));
                    linkDistanceRepository.save(new LinkDistance(destinationNodeId, startNodeId, callTotalTime));
                }
            }

        }

        return distanceMatrix;
    }

    public class GeneticAlgorithm {

        private final List<EmployeeDTO> employees;
        private final List<ElderlyDTO> elderlys;
        private final Map<String, Map<String, Integer>> distanceMatrix;


        public GeneticAlgorithm(List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                                Map<String, Map<String, Integer>> distanceMatrix) {
            this.employees = employees;
            this.elderlys = elderly;
            this.distanceMatrix = distanceMatrix;
        }


        public List<Chromosome> run() {
            // 초기 솔루션 생성
            List<Chromosome> chromosomes = generateInitialPopulation();

            // 반복
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                // 평가
                evaluatePopulation(chromosomes, employees, distanceMatrix);

                // 선택
                List<Chromosome> selectedChromosomes = selectParents(chromosomes);

                // 교차
                List<Chromosome> offspringChromosomes = crossover(selectedChromosomes, elderlys);

                // 돌연변이
                mutate(offspringChromosomes, elderlys.size());

                // 다음 세대 생성
                chromosomes = combinePopulations(chromosomes, offspringChromosomes);
            }

            // 최적의 솔루션 추출
            return chromosomes.stream().sorted().collect(Collectors.toList());
        }

        private List<Chromosome> generateInitialPopulation() {
            List<Chromosome> chromosomes = new ArrayList<>();
            for (int i = 0; i < POPULATION_SIZE; i++) {

                chromosomes.add(new Chromosome(employees, elderlys));

            }
            return chromosomes;
        }

        private void evaluatePopulation(List<Chromosome> chromosomes, List<EmployeeDTO> employee,
                                        Map<String, Map<String, Integer>> distanceMatrix) {
            for (Chromosome chromosome : chromosomes) {
                chromosome.setFitness(calculateFitness(chromosome, employees, distanceMatrix));
            }
        }

        private double calculateFitness(Chromosome chromosome, List<EmployeeDTO> employees,
                                        Map<String, Map<String, Integer>> distanceMatrix) {
            double fitness = 0.0;

            // 퇴근 시간 계산
            List<Double> departureTimes = calculateDepartureTimes(chromosome);
            chromosome.setDepartureTimes(departureTimes);

            // 모든 퇴근 시간의 합 계산
            double totalDepartureTime = departureTimes.stream().mapToDouble(Double::doubleValue).sum();

            for (int i = 0; i < chromosome.getGenes().size(); i++) {

                for (int j = 0; j < chromosome.getGenes().get(i).size() - 1; j++) {
                    int elderlyIndex1 = chromosome.getGenes().get(i).get(j);
                    int elderlyIndex2 = chromosome.getGenes().get(i).get(j + 1);

                    if (distanceMatrix.get("Elderly_" + elderlys.get(elderlyIndex1).id())
                            .get("Elderly_" + elderlys.get(elderlyIndex2).id()) == 0) {
                        fitness += 1;
                    }

                }
            }

            // 적합도 계산
            fitness = 1.0 / (totalDepartureTime + 1.0);

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

            return fitness;
        }

        private List<Double> calculateDepartureTimes(Chromosome chromosome) {
            List<Double> departureTimes = new ArrayList<>();

            for (int i = 0; i < chromosome.getGenes().size(); i++) {
                double departureTime = 0.0;
                for (int j = 0; j < chromosome.getGenes().get(i).size() - 1; j++) {
                    String company = "Company";
                    String startNodeId = "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j)).id();
                    String destinationNodeId = "Elderly_" + elderlys.get(chromosome.getGenes().get(i).get(j + 1)).id();

                    if (j == 0) {
                        departureTime += distanceMatrix.get(company).get("Elderly_" + elderlys.get(j).id());
                    }

                    departureTime += distanceMatrix.get(startNodeId).get(destinationNodeId);
                }

                departureTime += distanceMatrix.get(
                                "Elderly_" + elderlys.get(chromosome.getGenes().get(i).size() - 1).id())
                        .get("Employee_" + employees.get(i).id());

                departureTimes.add(departureTime);
            }

            return departureTimes;
        }

        private List<Chromosome> selectParents(List<Chromosome> chromosomes) {
            List<Chromosome> selectedChromosomes = new ArrayList<>();
            for (int i = 0; i < POPULATION_SIZE; i++) {
                // 룰렛 휠 선택
                int selectedIndex = rouletteWheelSelection(chromosomes);
                selectedChromosomes.add(chromosomes.get(selectedIndex));
            }
            return selectedChromosomes;
        }

        private int rouletteWheelSelection(List<Chromosome> chromosomes) {
            // 적합도 총합 계산
            double totalFitness = chromosomes.stream().mapToDouble(Chromosome::getFitness).sum();

            // 룰렛 휠 생성
            List<Double> rouletteWheel = new ArrayList<>();
            double cumulativeFitness = 0.0;
            for (Chromosome chromosome : chromosomes) {
                cumulativeFitness += chromosome.getFitness();
                rouletteWheel.add(cumulativeFitness / totalFitness);
            }

            // 랜덤 값 생성
            double randomValue = Math.random();

            // 선택된 인덱스 찾기
            int selectedIndex = 0;
            while (randomValue > rouletteWheel.get(selectedIndex)) {
                selectedIndex++;
            }

            return selectedIndex;
        }

        //        private List<Chromosome> crossover(List<Chromosome> selectedChromosomes) {
//            double crossoverRate = 0.85;
//
//            Random rand = new Random();
//            List<Chromosome> offspring = new ArrayList<>();
//
//            for (int i = 0; i < selectedChromosomes.size(); i += 2) {
//                Chromosome parent1 = selectedChromosomes.get(i);
//                Chromosome parent2 = selectedChromosomes.get(i + 1);
//
//                if (rand.nextDouble() < crossoverRate) {
//                    int crossoverPoint = rand.nextInt(parent1.getGenes().size());
//                    for (int j = crossoverPoint; j < parent1.getGenes().size(); j++) {
//                        List<Integer> temp = parent1.getGenes().get(j);
//                        parent1.getGenes().set(j, parent2.getGenes().get(j));
//                        parent2.getGenes().set(j, temp);
//                    }
//                }
//
//                offspring.add(parent1);
//                offspring.add(parent2);
//            }
//
//            selectedChromosomes.clear();
//            selectedChromosomes.addAll(offspring);
//
//            return selectedChromosomes;
//
//        }
        private List<Chromosome> crossover(List<Chromosome> selectedChromosomes, List<ElderlyDTO> elderlys) {
            double crossoverRate = 0.85;
            Random rand = new Random();
            List<Chromosome> offspring = new ArrayList<>();

            for (int i = 0; i < selectedChromosomes.size(); i += 2) {
                Chromosome parent1 = selectedChromosomes.get(i);
                Chromosome parent2 = selectedChromosomes.get(i + 1);

                if (i + 1 < selectedChromosomes.size() && rand.nextDouble() < crossoverRate) {
                    // Perform crossover only if there is a pair to crossover with
                    Chromosome child1 = parent1;
                    Chromosome child2 = parent2;

                    int crossoverPoint = rand.nextInt(parent1.getGenes().size());
                    for (int j = crossoverPoint; j < parent1.getGenes().size(); j++) {
                        // Swap the gene segments at the crossover point
                        child1.getGenes().set(j, new ArrayList<>(parent2.getGenes().get(j)));
                        child2.getGenes().set(j, new ArrayList<>(parent1.getGenes().get(j)));
                    }

                    // Validate and fix any duplicates within the children
                    fixDuplicateAssignments(child1, elderlys);
                    fixDuplicateAssignments(child2, elderlys);

                    offspring.add(child1);
                    offspring.add(child2);
                } else {
                    // If no crossover, add clones of the parents to the offspring
                    offspring.add(parent1);
                    offspring.add(parent2);
                }
            }

            return offspring;
        }

        private void fixDuplicateAssignments(Chromosome child, List<ElderlyDTO> elderlys) {
            Set<Integer> assignedElderly = new HashSet<>();
            for (List<Integer> gene : child.getGenes()) {
                for (int i = 0; i < gene.size(); i++) {
                    int elderlyId = gene.get(i);
                    if (!assignedElderly.add(elderlyId)) {
                        // Found a duplicate within this child, find a replacement
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


        private void mutate(List<Chromosome> offspringChromosomes, int numElderly) {

            Random rand = new Random();

            for (Chromosome chromosome : offspringChromosomes) {
                if (rand.nextDouble() < MUTATION_RATE) {
                    int mutationPoint1 = rand.nextInt(chromosome.getGenes().size());
                    int mutationPoint2 = rand.nextInt(chromosome.getGenes().get(mutationPoint1).size());
                    int newElderly = rand.nextInt(numElderly);
                    chromosome.getGenes().get(mutationPoint1).set(mutationPoint2, newElderly);
                }
            }

//            for (Chromosome offspring : offspringChromosomes) {
//                // 돌연변이 확률만큼 돌연변이 발생
//                if (Math.random() < MUTATION_RATE) {
//                    // 돌연변이 지점 랜덤하게 선택
//                    int mutationPoint = (int) (Math.random() * offspring.getGeneLength());
//
//                    // 돌연변이 발생
//                    int newValue = (int) (Math.random() * employees.size());
//                    offspring.setGene(mutationPoint, newValue);
//                }
//            }
        }

        private List<Chromosome> combinePopulations(List<Chromosome> chromosomes,
                                                    List<Chromosome> offspringChromosomes) {
            List<Chromosome> combinedChromosomes = new ArrayList<>();
            combinedChromosomes.addAll(chromosomes);
            combinedChromosomes.addAll(offspringChromosomes);

            // 정렬
            combinedChromosomes.stream().sorted().collect(Collectors.toList());

            // 최상위 개체만 선택
            return combinedChromosomes.subList(0, POPULATION_SIZE);
        }


    }

    public List<Location> findClosestLocations(RequestDispatchDTO requestDispatchDTO) {

        return null;
    }


}


