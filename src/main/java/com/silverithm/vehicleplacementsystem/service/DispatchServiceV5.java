package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.dto.AssignmentElderRequest;
import com.silverithm.vehicleplacementsystem.dto.AssignmentResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.CompanyDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.dto.KakaoMapApiResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.OsrmApiResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.RequestDispatchDTO;
import com.silverithm.vehicleplacementsystem.entity.ChromosomeV3;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@EnableCaching
public class DispatchServiceV5 {


    private final LinkDistanceRepository linkDistanceRepository;
    private final SSEService sseService;
    private final DispatchHistoryService dispatchHistoryService;

    private String key;
    private String kakaoKey;

    @Value("${osrm.server.url}")
    private String osrmServerUrl;


    public DispatchServiceV5(@Value("${tmap.key}") String key, @Value("${kakao.key}") String kakaoKey,
                             LinkDistanceRepository linkDistanceRepository,
                             SSEService sseService, DispatchHistoryService dispatchHistoryService
    ) {
        this.linkDistanceRepository = linkDistanceRepository;
        this.sseService = sseService;
        this.key = key;
        this.kakaoKey = kakaoKey;
        this.dispatchHistoryService = dispatchHistoryService;
    }

    public KakaoMapApiResponseDTO getDistanceTotalTimeWithTmapApi(Location startAddress,
                                                                  Location destAddress) throws NullPointerException {

        String distanceString = "0";
        String durationString = "0";
        try {
            RestTemplate restTemplate = new RestTemplate();

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoKey);

            // HTTP 엔터티 생성 (헤더 포함)
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // 파라미터 설정
            String origin = startAddress.getLongitude() + "," + startAddress.getLatitude();
            String destination = destAddress.getLongitude() + "," + destAddress.getLatitude();
            String waypoints = "";
            String priority = "DISTANCE";
            String carFuel = "GASOLINE";
            boolean carHipass = false;
            boolean alternatives = false;
            boolean roadDetails = false;

            // URL에 파라미터 추가
            String url = "https://apis-navi.kakaomobility.com/v1/directions" + "?origin=" + origin + "&destination="
                    + destination
                    + "&waypoints=" + waypoints + "&priority=" + priority + "&car_fuel=" + carFuel
                    + "&car_hipass=" + carHipass + "&alternatives=" + alternatives + "&road_details=" + roadDetails;

            // GET 요청 보내기
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            // result_code가 104이면 0 반환
            if (response.getBody().contains("\"result_code\":104")) {
                return new KakaoMapApiResponseDTO(0, 0); // 출발지와 도착지가 너무 가까운 경우 0 반환
            }

            distanceString = response.getBody().split("\"duration\":")[1].split("}")[0].trim();
            durationString = response.getBody().split("\"distance\":")[1].split(",")[0].trim();

        } catch (NullPointerException e) {
            throw new NullPointerException("[ERROR] KAKAOMAP API 요청에 실패하였습니다.");
        }

        log.info("Tmap API distance :  " + distanceString);

        return new KakaoMapApiResponseDTO(Integer.parseInt(durationString),
                Integer.parseInt(distanceString)); // 문자열을 정수형으로 변환

    }

    public OsrmApiResponseDTO getDistanceTotalTimeWithOsrmApi(Location startAddress,
                                                              Location destAddress) throws NullPointerException {
        String distanceString = "0";
        String durationString = "0";

        try {
            RestTemplate restTemplate = new RestTemplate();

            String coordinates = startAddress.getLongitude() + "," + startAddress.getLatitude() + ";"
                    + destAddress.getLongitude() + "," + destAddress.getLatitude();

            // table 대신 route 서비스 사용
            String url = osrmServerUrl + "/route/v1/driving/" + coordinates;

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            if (!"Ok".equals(root.get("code").asText())) {
                throw new RuntimeException("OSRM API returned non-OK status: " + root.get("code").asText());
            }

            JsonNode routesNode = root.get("routes");

            if (routesNode != null && routesNode.size() > 0) {
                JsonNode firstRoute = routesNode.get(0);

                // 전체 경로의 distance와 duration 추출
                double distance = firstRoute.get("distance").asDouble();
                double duration = firstRoute.get("duration").asDouble();

                durationString = String.valueOf((int) duration);  // 초 단위
                distanceString = String.valueOf((int) distance);  // 미터 단위

            } else {
                log.warn("No routes found in OSRM response: {}", response.getBody());
                throw new RuntimeException("No routes found in OSRM response");
            }

        } catch (Exception e) {
            log.error("OSRM API 요청 실패 - Error: {}", e.getMessage(), e);
            throw new NullPointerException("[ERROR] OSRM API 요청에 실패하였습니다. - " + e.getMessage());
        }

        log.info("OSRM API distance and duration : " + distanceString + " " + durationString);

        return new OsrmApiResponseDTO(Integer.parseInt(distanceString),
                Integer.parseInt(durationString));
    }

    public List<AssignmentResponseDTO> getOptimizedAssignments(RequestDispatchDTO requestDispatchDTO) throws Exception {

        List<EmployeeDTO> employees = requestDispatchDTO.employees();
        List<ElderlyDTO> elderlys = requestDispatchDTO.elderlys();
        List<CoupleRequestDTO> couples = requestDispatchDTO.couples();
        CompanyDTO company = requestDispatchDTO.company();
        List<FixedAssignmentsDTO> fixedAssignments = requestDispatchDTO.fixedAssignments();

        sseService.notify(requestDispatchDTO.userName(), 5);

        // 거리 행렬 계산
        Map<String, Map<String, Integer>> distanceMatrix = calculateDistanceMatrix(employees, elderlys, company,
                requestDispatchDTO.dispatchType());
        sseService.notify(requestDispatchDTO.userName(), 15);

        // 유전 알고리즘 실행
        GeneticAlgorithmV5 geneticAlgorithm = new GeneticAlgorithmV5(employees, elderlys,
                couples,
                fixedAssignments,
                sseService);
        geneticAlgorithm.initialize(distanceMatrix, requestDispatchDTO.dispatchType(), requestDispatchDTO.userName());

        List<ChromosomeV3> chromosomes = geneticAlgorithm.run();
        // 최적의 솔루션 추출
        ChromosomeV3 bestChromosome = chromosomes.get(0);

        List<Double> departureTimes = bestChromosome.getDepartureTimes();
        sseService.notify(requestDispatchDTO.userName(), 95);

        List<AssignmentResponseDTO> assignmentResponseDTOS = createResult(
                employees, elderlys, bestChromosome, departureTimes, requestDispatchDTO.dispatchType());

        dispatchHistoryService.saveDispatchResult(assignmentResponseDTOS);

        log.info("done : " + bestChromosome.getGenes().toString() + " " + bestChromosome.getFitness() + " "
                + bestChromosome.getDepartureTimes());

        log.info(assignmentResponseDTOS.toString());

        sseService.notify(requestDispatchDTO.userName(), 100);

        return assignmentResponseDTOS;
    }

    private List<AssignmentResponseDTO> createResult(List<EmployeeDTO> employees,
                                                     List<ElderlyDTO> elderlys, ChromosomeV3 bestChromosome,
                                                     List<Double> departureTimes, DispatchType dispatchType) {
        List<AssignmentResponseDTO> assignmentResponseDTOS = new ArrayList<>();

        for (int i = 0; i < employees.size(); i++) {
            List<AssignmentElderRequest> assignmentElders = new ArrayList<>();

            for (int j = 0; j < bestChromosome.getGenes()[i].length; j++) {
                assignmentElders.add(
                        new AssignmentElderRequest(elderlys.get(bestChromosome.getGenes()[i][j]).id(),
                                elderlys.get(bestChromosome.getGenes()[i][j]).homeAddress(),
                                elderlys.get(bestChromosome.getGenes()[i][j]).name()));
            }
            assignmentResponseDTOS.add(
                    new AssignmentResponseDTO(dispatchType, employees.get(i).id(), employees.get(i).homeAddress(),
                            employees.get(i).workplace(),
                            employees.get(i).name(),
                            (int) (departureTimes.get(i) - 0), assignmentElders));
        }
        return assignmentResponseDTOS;
    }

    private static final String COMPANY_PREFIX = "Company";
    private static final String EMPLOYEE_PREFIX = "Employee_";
    private static final String ELDERLY_PREFIX = "Elderly_";

    private Map<String, Map<String, Integer>> calculateDistanceMatrix(List<EmployeeDTO> employees,
                                                                      List<ElderlyDTO> elderlys,
                                                                      CompanyDTO company,
                                                                      DispatchType dispatchType) {
        Map<String, Map<String, Integer>> distanceMatrix = initializeDistanceMatrix(employees, elderlys);
        StringBuilder idBuilder = new StringBuilder(32); // 적절한 초기 용량 설정

        // Company to Elderly connections
        for (ElderlyDTO elderly : elderlys) {
            String destinationNodeId = buildElderlyId(idBuilder, elderly.id().toString());
            processConnection(COMPANY_PREFIX, destinationNodeId, company.companyAddress(),
                    elderly.homeAddress(), distanceMatrix, dispatchType);
        }

        // Elderly to Elderly connections
        for (int i = 0; i < elderlys.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {
                if (i == j) continue;

                ElderlyDTO elderly1 = elderlys.get(i);
                ElderlyDTO elderly2 = elderlys.get(j);

                String startNodeId = buildElderlyId(idBuilder, elderly1.id().toString());
                String destinationNodeId = buildElderlyId(idBuilder, elderly2.id().toString());

                processConnection(startNodeId, destinationNodeId, elderly1.homeAddress(),
                        elderly2.homeAddress(), distanceMatrix, dispatchType);
            }
        }

        // Employee to Elderly connections
        for (EmployeeDTO employee : employees) {
            String startNodeId = buildEmployeeId(idBuilder, employee.id().toString());

            for (ElderlyDTO elderly : elderlys) {
                String destinationNodeId = buildElderlyId(idBuilder, elderly.id().toString());

                processConnection(startNodeId, destinationNodeId, employee.homeAddress(),
                        elderly.homeAddress(), distanceMatrix, dispatchType);
            }
        }

        return distanceMatrix;
    }

    private Map<String, Map<String, Integer>> initializeDistanceMatrix(List<EmployeeDTO> employees, List<ElderlyDTO> elderlys) {
        int expectedSize = 1 + employees.size() + elderlys.size(); // Company + Employees + Elderlies
        Map<String, Map<String, Integer>> distanceMatrix = new HashMap<>(expectedSize);
        StringBuilder idBuilder = new StringBuilder(32);

        distanceMatrix.put(COMPANY_PREFIX, new HashMap<>());

        for (EmployeeDTO employee : employees) {
            distanceMatrix.put(buildEmployeeId(idBuilder, employee.id().toString()), new HashMap<>());
        }

        for (ElderlyDTO elderly : elderlys) {
            distanceMatrix.put(buildElderlyId(idBuilder, elderly.id().toString()), new HashMap<>());
        }

        return distanceMatrix;
    }

    private String buildEmployeeId(StringBuilder builder, String id) {
        builder.setLength(0); // clear builder
        return builder.append(EMPLOYEE_PREFIX).append(id).toString();
    }

    private String buildElderlyId(StringBuilder builder, String id) {
        builder.setLength(0); // clear builder
        return builder.append(ELDERLY_PREFIX).append(id).toString();
    }

    private record Distance(int duration, int distance) {}

    private Distance getDistance(String startNodeId, String endNodeId, Location startAddress, Location endAddress) {
        Optional<LinkDistance> linkDistance = linkDistanceRepository
                .findNodeByStartNodeIdAndDestinationNodeId(startNodeId, endNodeId);

        if (linkDistance.isPresent()) {
            return new Distance(
                    linkDistance.get().getTotalTime(),
                    linkDistance.get().getTotalDistance()
            );
        }

        OsrmApiResponseDTO response = getDistanceTotalTimeWithOsrmApi(startAddress, endAddress);

        // Save both directions
        LinkDistance link = new LinkDistance(startNodeId, endNodeId,
                response.duration(), response.distance());
        linkDistanceRepository.save(link);
        linkDistanceRepository.save(new LinkDistance(endNodeId, startNodeId,
                response.duration(), response.distance()));

        return new Distance(response.duration(), response.distance());
    }

    private void processConnection(
            String startNodeId,
            String endNodeId,
            Location startAddress,
            Location endAddress,
            Map<String, Map<String, Integer>> distanceMatrix,
            DispatchType dispatchType
    ) {
        Distance distance = getDistance(startNodeId, endNodeId, startAddress, endAddress);
        updateDistanceMatrix(startNodeId, endNodeId, distance, distanceMatrix, dispatchType);
    }

    private void updateDistanceMatrix(
            String start,
            String end,
            Distance distance,
            Map<String, Map<String, Integer>> distanceMatrix,
            DispatchType dispatchType
    ) {
        int value = switch (dispatchType) {
            case DISTANCE_IN, DISTANCE_OUT -> distance.distance();
            case DURATION_IN, DURATION_OUT -> distance.duration();
        };

        distanceMatrix.get(start).put(end, value);
        distanceMatrix.get(end).put(start, value);
    }


}


