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
import java.util.Map;
import java.util.Optional;
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

    private static int MAX_ITERATIONS = 1000;
    //50~100
    private static int POPULATION_SIZE = 100;
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

//        List<Employee> employees = new ArrayList<>();
//        List<Elderly> elderly = new ArrayList<>();

        int requiredFrontSeat = 1;

//        Company company = new Company(new Location(37.365199444, 127.10323));

//        employees.add(new Employee(new Location(37.36519974258491, 127.10323758),
//                new Location(37.36519974258491, 127.10323758), 5));
//        employees.add(new Employee(new Location(37.36519974258491, 127.10323751),
//                new Location(37.36519974258491, 127.10323755), 5));
//        employees.add(new Employee(new Location(37.36519974258491, 127.10323752),
//                new Location(37.36519974258491, 127.10323754), 5));
//        employees.add(new Employee(new Location(37.36519974258491, 127.10323753),
//                new Location(37.36519974258491, 127.10323753), 5));
//        employees.add(new Employee(new Location(37.36519974258491, 127.10323754),
//                new Location(37.36519974258491, 127.10323752), 5));
//
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), true));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), true));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), true));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));
//        elderly.add(new Elderly(new Location(37.36519974258491, 127.10323758), false));

        // 거리 행렬 계산
        Map<String, Map<String, Integer>> distanceMatrix = calculateDistanceMatrix(employees, elderlys, company);

        // 유전 알고리즘 실행
        GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(employees, elderlys, distanceMatrix,
                requiredFrontSeat);
        List<Chromosome> chromosomes = geneticAlgorithm.run();

        // 최적의 솔루션 추출
        Chromosome bestChromosome = chromosomes.get(0);

        System.out.println(bestChromosome.getDepartureTimes());
        System.out.println(bestChromosome.getGenes());

//        // 퇴근 시간 및 배치 정보 계산
//        for (int i = 0; i < employees.size(); i++) {
//            int employeeIndex = i * 5;
//
//            employees.get(i).setDepartureTime(
//                    bestChromosome.getGene(employeeIndex) + "," +
//                            bestChromosome.getGene(employeeIndex + 1) + "," +
//                            bestChromosome.getGene(employeeIndex + 2) + "," +
//                            bestChromosome.getGene(employeeIndex + 3) + "," +
//                            bestChromosome.getGene(employeeIndex + 4)
//            );
//        }

//        // 퇴근 시간 및 방문 순서 계산
//        for (Employee employee : employees) {
//            List<Integer> elderlyIndices = new ArrayList<>();
//            for (int i = 0; i < employee.getDepartureTime().length(); i++) {
//                elderlyIndices.add(Integer.parseInt(employee.getDepartureTime().split(",")[i]));
//            }
//
//            // 퇴근 시간 계산
//            double departureTime = 0.0;
//            for (int i = 0; i < elderlyIndices.size() - 1; i++) {
//                departureTime += distanceMatrix.get(elderly.get(elderlyIndices.get(i)))
//                        .get(elderly.get(elderlyIndices.get(i + 1)));
//            }
//            employee.setDepartureTime(String.valueOf(departureTime));
//
//            // 방문 순서 계산
//            String visitOrder = "";
//            for (int i = 0; i < elderlyIndices.size(); i++) {
//                visitOrder += elderlyIndices.get(i) + ", ";
//            }
//            employee.setVisitOrder(visitOrder.substring(0, visitOrder.length() - 2));
//        }

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
        private final List<ElderlyDTO> elderly;
        private final Map<String, Map<String, Integer>> distanceMatrix;
        private final int requiredFrontSeat;


        public GeneticAlgorithm(List<EmployeeDTO> employees, List<ElderlyDTO> elderly,
                                Map<String, Map<String, Integer>> distanceMatrix, int requiredFrontSeat) {
            this.employees = employees;
            this.elderly = elderly;
            this.distanceMatrix = distanceMatrix;
            this.requiredFrontSeat = requiredFrontSeat;
        }

        public List<Chromosome> run() {
            // 초기 솔루션 생성
            List<Chromosome> chromosomes = generateInitialPopulation();

            // 반복
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                // 평가
                evaluatePopulation(chromosomes, employees);

                // 선택
                List<Chromosome> selectedChromosomes = selectParents(chromosomes);

                // 교차
                List<Chromosome> offspringChromosomes = crossover(selectedChromosomes);

                // 돌연변이
                mutate(offspringChromosomes);

                // 다음 세대 생성
                chromosomes = combinePopulations(chromosomes, offspringChromosomes);
            }

            // 최적의 솔루션 추출
            return chromosomes.stream().sorted().collect(Collectors.toList());
        }

        private List<Chromosome> generateInitialPopulation() {
            List<Chromosome> chromosomes = new ArrayList<>();
            for (int i = 0; i < POPULATION_SIZE; i++) {

                chromosomes.add(new Chromosome(employees, elderly, requiredFrontSeat));

            }
            return chromosomes;
        }

        private void evaluatePopulation(List<Chromosome> chromosomes, List<EmployeeDTO> employees) {
            for (Chromosome chromosome : chromosomes) {
                chromosome.setFitness(calculateFitness(chromosome, employees));
            }
        }

        private double calculateFitness(Chromosome chromosome, List<EmployeeDTO> employees) {
            double fitness = 0.0;

            // 퇴근 시간 계산
            List<Double> departureTimes = calculateDepartureTimes(chromosome);
            chromosome.setDepartureTimes(departureTimes);

            // 최대 퇴근 시간 계산
            double maxDepartureTime = departureTimes.stream().max(Double::compareTo).get();

            // 적합도 계산
            fitness = 1.0 / (maxDepartureTime + 1.0);

            // 앞자리에 필수로 타야 하는 노인이 실제로 앞자리에 배정되었는지 확인

            int geneIndex = 0;
            for (int i = 0; i < employees.size(); i++) {
                int frontSeatCount = 0;
                for (int j = 0; j < employees.get(i).maximumCapacity(); j++) {
                    if (elderly.get(chromosome.getGene(geneIndex)).requiredFrontSeat()) {
                        frontSeatCount++;
                    }
                    geneIndex++;
                }

                if (frontSeatCount > 1) {
                    fitness = 0.0;
                }

                if (geneIndex >= chromosome.getGeneLength()) {
                    break;
                }

            }

//            for (int i = 0; i < chromosome.getGeneLength(); i++) {
//                if (elderly.get(chromosome.getGene(i)).isRequiredFrontSeat()) {
//                    frontSeatCount++;
//                }
//            }
//
//            if (frontSeatCount < requiredFrontSeat) {
//                fitness = 0.0;
//            }

            return fitness;
        }

        private List<Double> calculateDepartureTimes(Chromosome chromosome) {
            List<Double> departureTimes = new ArrayList<>();

            double departureTime = 0.0;

            int maximumCapacity = employees.get(0).maximumCapacity();
            int employeeIndex = 0;
            int capacityIndex = 1;

            for (int i = 0; i < chromosome.getGeneLength() - 1; i++) {

                if (capacityIndex < maximumCapacity) {
                    departureTime += distanceMatrix.get(elderly.get(chromosome.getGene(i)))
                            .get(elderly.get(chromosome.getGene(i + 1)));
                }

                if (capacityIndex >= maximumCapacity) {
//                    departureTime += callTMapAPI(employees.get(maximumCapacityIndex).getWorkplace(),
//                            elderly.get(chromosome.getGene(capacityIndex - maximumCapacity)).getHomeAddress());
//
//                    departureTime += callTMapAPI(elderly.get(capacityIndex).getHomeAddress(),
//                            employees.get(maximumCapacityIndex).getHomeAddress());

                    if (employeeIndex < employees.size()) {
                        employeeIndex++;
                        maximumCapacity = employees.get(employeeIndex).maximumCapacity();
                    }
                    capacityIndex = 0;
                    departureTimes.add(departureTime);
                    departureTime = 0;
                }

                capacityIndex++;
            }

            if (departureTime > 0) {
//                departureTime += callTMapAPI(employees.get(maximumCapacityIndex).getWorkplace(),
//                        elderly.get(chromosome.getGene(capacityIndex - maximumCapacity)).getHomeAddress());
//
//                departureTime += callTMapAPI(elderly.get(capacityIndex).getHomeAddress(),
//                        employees.get(maximumCapacityIndex).getHomeAddress());
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

        private List<Chromosome> crossover(List<Chromosome> selectedChromosomes) {
            List<Chromosome> offspringChromosomes = new ArrayList<>();
            for (int i = 0; i < selectedChromosomes.size(); i += 2) {
                // 일점 교차
                offspringChromosomes.add(
                        singlePointCrossover(selectedChromosomes.get(i), selectedChromosomes.get(i + 1)));
            }
            return offspringChromosomes;
        }

        private Chromosome singlePointCrossover(Chromosome parent1, Chromosome parent2) {
            // 교차 지점 랜덤하게 선택
            int crossoverPoint = (int) (Math.random() * parent1.getGeneLength());

            // 자식 생성
            Chromosome offspring = new Chromosome(employees, elderly, requiredFrontSeat);
            for (int i = 0; i < crossoverPoint; i++) {
                offspring.setGene(i, parent1.getGene(i));
            }
            for (int i = crossoverPoint; i < parent1.getGeneLength(); i++) {
                offspring.setGene(i, parent2.getGene(i));
            }

            return offspring;
        }

        private void mutate(List<Chromosome> offspringChromosomes) {
            for (Chromosome offspring : offspringChromosomes) {
                // 돌연변이 확률만큼 돌연변이 발생
                if (Math.random() < MUTATION_RATE) {
                    // 돌연변이 지점 랜덤하게 선택
                    int mutationPoint = (int) (Math.random() * offspring.getGeneLength());

                    // 돌연변이 발생
                    int newValue = (int) (Math.random() * employees.size());
                    offspring.setGene(mutationPoint, newValue);
                }
            }
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

        //elderlyLocations, EmployeeDestinations

        //35.150735954515866, 경도는 128.1176559038332
        //클릭 위치의 위도는 35.15324955862964, 경도는 128.11898796967847 입니다
        //클릭 위치의 위도는 35.15576496372367, 경도는 128.1135242147227 입니다
        //클릭 위치의 위도는 35.15278031585356, 경도는 128.1075246301955 입니다
        //클릭 위치의 위도는 35.17489982781904, 경도는 128.11521211264545 입니다
        //클릭 위치의 위도는 35.180357981662894, 경도는 128.0858662223393 입니다
        //클릭 위치의 위도는 35.16580451988244, 경도는 128.05186706278897 입니다
        //클릭 위치의 위도는 35.214320873081014, 경도는 128.14817083106618 입니다

        //{
        //  "elderlyLocations": [
        //    {"x": 10, "y": 20},
        //    {"x": 15, "y": 25},
        //    {"x": 30, "y": 40},
        //    {"x": 45, "y": 50}
        //  ],
        //  "employeeLocations": [
        //    {"x": 10, "y": 20},
        //    {"x": 15, "y": 25},
        //    {"x": 30, "y": 40},
        //    {"x": 45, "y": 50}
        //  ],
        //}

        return null;
    }


}


