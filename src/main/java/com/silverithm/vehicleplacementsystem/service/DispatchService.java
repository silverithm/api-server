package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.CompanyDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DispatchService {

    //가까운 거리일수록 가중치가 더 들어가야함 - 가까운 거리를 붙여주어야함
    //고정 시키는 인원이 주어지면 해당 직원에게 고정 인원이 없으면 점수를 0으로 해주어야함

    private static int MAX_ITERATIONS = 300;
    private static int POPULATION_SIZE = 20000;
    private static double MUTATION_RATE = 0.9;
    private static double CROSSOVER_RATE = 0.7;


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
        log.info("Tmap API :  " + totalTime);
        return totalTime;

    }

    public List<AssignmentResponseDTO> getOptimizedAssignments(RequestDispatchDTO requestDispatchDTO) throws Exception {

        List<EmployeeDTO> employees = requestDispatchDTO.employees();
        List<ElderlyDTO> elderlys = requestDispatchDTO.elderlys();
        CompanyDTO company = requestDispatchDTO.company();
        List<FixedAssignmentsDTO> fixedAssignments = requestDispatchDTO.fixedAssignments();

        // 거리 행렬 계산
        Map<String, Map<String, Integer>> distanceMatrix = calculateDistanceMatrix(employees, elderlys, company);
        // 유전 알고리즘 실행
        GeneticAlgorithmService geneticAlgorithm = new GeneticAlgorithmService(employees, elderlys, distanceMatrix,
                fixedAssignments,
                requestDispatchDTO.dispatchType());

        List<Chromosome> chromosomes = geneticAlgorithm.run();

        // 최적의 솔루션 추출
        Chromosome bestChromosome = chromosomes.get(0);

        double sum = 0;
        for (double value : bestChromosome.getDepartureTimes()) {
            sum += value;
        }

        log.info("done : " + bestChromosome.getGenes().toString() + " " + bestChromosome.getFitness());

        List<AssignmentResponseDTO> assignmentResponseDTOS = new ArrayList<>();

        List<Double> departureTimes = bestChromosome.getDepartureTimes();
        for (int i = 0; i < employees.size(); i++) {
            List<String> assignmentElderNames = new ArrayList<>();

            for (int j = 0; j < bestChromosome.getGenes().get(i).size(); j++) {
                assignmentElderNames.add(elderlys.get(bestChromosome.getGenes().get(i).get(j)).name());
            }
            assignmentResponseDTOS.add(new AssignmentResponseDTO(employees.get(i).name(),
                    (int) (departureTimes.get(i) / 60), assignmentElderNames));
        }
        return assignmentResponseDTOS;
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
                distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
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
                if (i == j) {
                    continue;
                }

                String startNodeId = "Elderly_" + elderlys.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(
                        startNodeId,
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

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(
                        startNodeId,
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


}


