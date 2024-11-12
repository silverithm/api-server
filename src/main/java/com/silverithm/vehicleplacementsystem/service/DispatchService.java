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
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
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


    private final LinkDistanceRepository linkDistanceRepository;
    private final SSEService sseService;

    private String key;
    private String kakaoKey;


    public DispatchService(@Value("${tmap.key}") String key, @Value("${kakao.key}") String kakaoKey,
                           LinkDistanceRepository linkDistanceRepository,
                           SSEService sseService) {
        this.linkDistanceRepository = linkDistanceRepository;
        this.sseService = sseService;
        this.key = key;
        this.kakaoKey = kakaoKey;
    }

//    public int getDistanceTotalTimeWithTmapApi(Location startAddress,
//                                               Location destAddress) throws NullPointerException {
//
//        int totalTime = 0;
//        try {
//
//            String url = "https://apis.openapi.sk.com/tmap/routes?version=1";
//
//            // 요청 헤더 설정
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("Accept", "application/json");
//            headers.set("Content-Type", "application/json");
//            headers.set("appKey", key);
//
//            // 요청 데이터 설정
//            String requestBody = String.format(
//                    "{\"roadType\":32, \"startX\":%.8f, \"startY\":%.8f, \"endX\":%.8f, \"endY\":%.8f}",
//                    startAddress.getLongitude(), startAddress.getLatitude(), destAddress.getLongitude(),
//                    destAddress.getLatitude());
//
//            // HTTP 요청 엔티티 생성
//            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
//
//            // RestTemplate 생성
//            RestTemplate restTemplate = new RestTemplate();
//
//            // API 호출
//            ResponseEntity<String> responseEntity = restTemplate.exchange(
//                    url,
//                    HttpMethod.POST,
//                    requestEntity,
//                    String.class
//            );
//
//            totalTime = Integer.parseInt(responseEntity.getBody().split("\"totalDistance\":")[1].split(",")[0].trim());
//
//        } catch (NullPointerException e) {
//            e.printStackTrace();
//            throw new NullPointerException("[ERROR] TMAP API 요청에 실패하였습니다.");
//        }
//
//        log.info("Tmap API distance :  " + totalTime);
//
//        return totalTime;
//    }


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
            String url = "http://osrm-server:5000/route/v1/driving/" + coordinates;

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

                log.info("Parsed values - Distance: {} meters, Duration: {} seconds", distance, duration);
            } else {
                log.warn("No routes found in OSRM response: {}", response.getBody());
                throw new RuntimeException("No routes found in OSRM response");
            }

        } catch (Exception e) {
            log.error("OSRM API 요청 실패 - Error: {}", e.getMessage(), e);
            throw new NullPointerException("[ERROR] OSRM API 요청에 실패하였습니다. - " + e.getMessage());
        }

        log.info("OSRM API distance: " + distanceString);
        log.info("OSRM API duration: " + durationString);

        return new OsrmApiResponseDTO(Integer.parseInt(durationString),
                Integer.parseInt(distanceString));
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
        GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(employees, elderlys, couples, distanceMatrix,
                fixedAssignments,
                requestDispatchDTO.dispatchType(), requestDispatchDTO.userName(),
                sseService);

        List<Chromosome> chromosomes = geneticAlgorithm.run();
        // 최적의 솔루션 추출
        Chromosome bestChromosome = chromosomes.get(0);

        List<Double> departureTimes = bestChromosome.getDepartureTimes();
        sseService.notify(requestDispatchDTO.userName(), 95);

        List<AssignmentResponseDTO> assignmentResponseDTOS = createResult(
                employees, elderlys, bestChromosome, departureTimes, requestDispatchDTO.dispatchType());

        log.info("done : " + bestChromosome.getGenes().toString() + " " + bestChromosome.getFitness() + " "
                + bestChromosome.getDepartureTimes());

        log.info(assignmentResponseDTOS.toString());

//        for (Map.Entry<String, Map<String, Integer>> outerEntry : distanceMatrix.entrySet()) {
//            String outerKey = outerEntry.getKey();
//            Map<String, Integer> innerMap = outerEntry.getValue();
//
//            for (Map.Entry<String, Integer> innerEntry : innerMap.entrySet()) {
//                String innerKey = innerEntry.getKey();
//                Integer value = innerEntry.getValue();
//
//                log.info("Outer Key: " + outerKey + ", Inner Key: " + innerKey + ", Value: " + value);
//            }
//        }

        sseService.notify(requestDispatchDTO.userName(), 100);

        return assignmentResponseDTOS;
    }

    private List<AssignmentResponseDTO> createResult(List<EmployeeDTO> employees,
                                                     List<ElderlyDTO> elderlys, Chromosome bestChromosome,
                                                     List<Double> departureTimes, DispatchType dispatchType) {
        List<AssignmentResponseDTO> assignmentResponseDTOS = new ArrayList<>();

        for (int i = 0; i < employees.size(); i++) {
            List<AssignmentElderRequest> assignmentElders = new ArrayList<>();

            for (int j = 0; j < bestChromosome.getGenes().get(i).size(); j++) {
                assignmentElders.add(
                        new AssignmentElderRequest(elderlys.get(bestChromosome.getGenes().get(i).get(j)).id(),
                                elderlys.get(bestChromosome.getGenes().get(i).get(j)).homeAddress(),
                                elderlys.get(bestChromosome.getGenes().get(i).get(j)).name()));
            }
            assignmentResponseDTOS.add(
                    new AssignmentResponseDTO(dispatchType, employees.get(i).id(), employees.get(i).homeAddress(),
                            employees.get(i).workplace(),
                            employees.get(i).name(),
                            (int) (departureTimes.get(i) / 60), assignmentElders));
        }
        return assignmentResponseDTOS;
    }

    private Map<String, Map<String, Integer>> calculateDistanceMatrix(List<EmployeeDTO> employees,
                                                                      List<ElderlyDTO> elderlys,
                                                                      CompanyDTO company, DispatchType dispatchType) {
        Map<String, Map<String, Integer>> distanceMatrix = new HashMap<>();

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
            Optional<Integer> totalDistance = linkDistanceRepository.findDistanceByStartNodeIdAndDestinationNodeId(
                    startNodeId,
                    destinationNodeId);

            Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정
            Integer totalDistanceValue = totalDistance.orElse(0); // 값이 없으면 0으로 기본값 설정

            if (totalTime.isPresent() && totalDistance.isPresent()) {

                if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, totalDistanceValue);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, totalDistanceValue);
                }

                if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
                    distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
                }

            }

            if (totalTime.isEmpty() || totalDistance.isEmpty()) {

                OsrmApiResponseDTO osrmApiResponseDTO = getDistanceTotalTimeWithOsrmApi(company.companyAddress(),
                        elderlys.get(i).homeAddress());

                if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.distance());
                    distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.distance());
                }

                if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.duration());
                    distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.duration());
                }

                linkDistanceRepository.save(
                        new LinkDistance(startNodeId, destinationNodeId, osrmApiResponseDTO.duration(),
                                osrmApiResponseDTO.distance()));
                linkDistanceRepository.save(
                        new LinkDistance(destinationNodeId, startNodeId, osrmApiResponseDTO.duration(),
                                osrmApiResponseDTO.distance()));
            }

        }

        for (int i = 0; i < elderlys.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {
                if (i == j) {
                    continue;
                }

                String startNodeId = "Elderly_" + elderlys.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(startNodeId,
                        destinationNodeId);
                Optional<Integer> totalDistance = linkDistanceRepository.findDistanceByStartNodeIdAndDestinationNodeId(
                        startNodeId,
                        destinationNodeId);

                Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정
                Integer totalDistanceValue = totalDistance.orElse(0); // 값이 없으면 0으로 기본값 설정

                if (totalTime.isPresent() && totalDistance.isPresent()) {

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, totalDistanceValue);
                        distanceMatrix.get(destinationNodeId).put(startNodeId, totalDistanceValue);
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
                        distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
                    }

                }

                if (totalTime.isEmpty() || totalDistance.isEmpty()) {

                    OsrmApiResponseDTO osrmApiResponseDTO = getDistanceTotalTimeWithOsrmApi(
                            elderlys.get(i).homeAddress(),
                            elderlys.get(j).homeAddress());

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.distance());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.distance());
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.duration());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.duration());
                    }

                    linkDistanceRepository.save(
                            new LinkDistance(startNodeId, destinationNodeId, osrmApiResponseDTO.duration(),
                                    osrmApiResponseDTO.distance()));
                    linkDistanceRepository.save(
                            new LinkDistance(destinationNodeId, startNodeId, osrmApiResponseDTO.duration(),
                                    osrmApiResponseDTO.distance()));
                }
            }

        }

        for (int i = 0; i < employees.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {

                String startNodeId = "Employee_" + employees.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                Optional<Integer> totalTime = linkDistanceRepository.findByStartNodeIdAndDestinationNodeId(startNodeId,
                        destinationNodeId);
                Optional<Integer> totalDistance = linkDistanceRepository.findDistanceByStartNodeIdAndDestinationNodeId(
                        startNodeId,
                        destinationNodeId);

                Integer totalTimeValue = totalTime.orElse(0); // 값이 없으면 0으로 기본값 설정
                Integer totalDistanceValue = totalDistance.orElse(0); // 값이 없으면 0으로 기본값 설정

                if (totalTime.isPresent()) {

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, totalDistanceValue);
                        distanceMatrix.get(destinationNodeId).put(startNodeId, totalDistanceValue);
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, totalTimeValue);
                        distanceMatrix.get(destinationNodeId).put(startNodeId, totalTimeValue);
                    }

                }

                if (totalTime.isEmpty()) {

                    OsrmApiResponseDTO osrmApiResponseDTO = getDistanceTotalTimeWithOsrmApi(
                            employees.get(i).homeAddress(),
                            elderlys.get(j).homeAddress());

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.distance());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.distance());
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, osrmApiResponseDTO.duration());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, osrmApiResponseDTO.duration());
                    }

                    linkDistanceRepository.save(
                            new LinkDistance(startNodeId, destinationNodeId, osrmApiResponseDTO.duration(),
                                    osrmApiResponseDTO.distance()));
                    linkDistanceRepository.save(
                            new LinkDistance(destinationNodeId, startNodeId, osrmApiResponseDTO.duration(),
                                    osrmApiResponseDTO.distance()));
                }
            }

        }

        return distanceMatrix;
    }


}


