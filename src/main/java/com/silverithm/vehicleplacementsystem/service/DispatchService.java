package com.silverithm.vehicleplacementsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
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
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@EnableCaching
public class DispatchService {

    private static final int MAX_DISPATCH_LIMIT = 5;
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    private final LinkDistanceRepository linkDistanceRepository;
    private final SSEService sseService;
    private final DispatchHistoryService dispatchHistoryService;
    private final UserRepository userRepository;

    private String key;
    private String kakaoKey;

    @Value("${osrm.server.url}")
    private String osrmServerUrl;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Qualifier("dispatchQueue")
    @Autowired
    private Queue dispatchQueue;
    @Autowired
    private RedisUtils redisUtils;

    public DispatchService(@Value("${tmap.key}") String key, @Value("${kakao.key}") String kakaoKey,
                           LinkDistanceRepository linkDistanceRepository,
                           SSEService sseService, DispatchHistoryService dispatchHistoryService,
                           UserRepository userRepository
    ) {
        this.linkDistanceRepository = linkDistanceRepository;
        this.sseService = sseService;
        this.key = key;
        this.kakaoKey = kakaoKey;
        this.dispatchHistoryService = dispatchHistoryService;
        this.userRepository = userRepository;
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

                log.info("Parsed values - Distance: {} meters, Duration: {} seconds", distance, duration);
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

    public List<AssignmentResponseDTO> getOptimizedAssignments(RequestDispatchDTO requestDispatchDTO)
            throws Exception {

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
        GeneticAlgorithm geneticAlgorithm = new GeneticAlgorithm(employees, elderlys,
                couples,
                fixedAssignments,
                sseService);
        geneticAlgorithm.initialize(distanceMatrix, requestDispatchDTO.dispatchType(), requestDispatchDTO.userName());

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
                            (int) (departureTimes.get(i) - 0), assignmentElders, employees.get(i).isDriver()));
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

            Optional<LinkDistance> linkDistance = linkDistanceRepository.findNodeByStartNodeIdAndDestinationNodeId(
                    startNodeId, destinationNodeId);

            if (linkDistance.isPresent()) {

                if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalDistance());
                    distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalDistance());
                }

                if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                    distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalTime());
                    distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalTime());
                }

            } else {
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

                Optional<LinkDistance> linkDistance = linkDistanceRepository.findNodeByStartNodeIdAndDestinationNodeId(
                        startNodeId, destinationNodeId);

                if (linkDistance.isPresent()) {

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalDistance());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalDistance());
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalTime());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalTime());
                    }

                } else {
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

                Optional<LinkDistance> linkDistance = linkDistanceRepository.findNodeByStartNodeIdAndDestinationNodeId(
                        startNodeId, destinationNodeId);

                if (linkDistance.isPresent()) {

                    if (dispatchType == DispatchType.DISTANCE_IN || dispatchType == DispatchType.DISTANCE_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalDistance());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalDistance());
                    }

                    if (dispatchType == DispatchType.DURATION_IN || dispatchType == DispatchType.DURATION_OUT) {
                        distanceMatrix.get(startNodeId).put(destinationNodeId, linkDistance.get().getTotalTime());
                        distanceMatrix.get(destinationNodeId).put(startNodeId, linkDistance.get().getTotalTime());
                    }

                } else {

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


    public String requestDispatchWithRabbitMQ(RequestDispatchDTO requestDispatchDTO, UserDetails userDetails,
                                              String jobId)
            throws JsonProcessingException, CustomException {

        if (isLimitExceeded(userDetails)) {
            log.info("Daily request limit exceeded for user: {}", userDetails.getUsername());
            throw new CustomException("배차 요청이 일일 제한을 초과했습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        return sendMessage(requestDispatchDTO, userDetails, jobId);
    }

    private String sendMessage(RequestDispatchDTO requestDispatchDTO, UserDetails userDetails, String jobId)
            throws JsonProcessingException {


        Message message = MessageBuilder
                .withBody(objectMapper.writeValueAsBytes(requestDispatchDTO))
                .setHeader("jobId", jobId)
                .setHeader("username", userDetails.getUsername())
                .build();

        rabbitTemplate.convertAndSend(dispatchQueue.getName(), message);

        return jobId;
    }

    public Boolean isLimitExceeded(UserDetails userDetails) {

        AppUser user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User Not Found", HttpStatus.UNPROCESSABLE_ENTITY));

        if (user.isActiveSubscription()) {
            return false;
        }

        return redisUtils.isExceededDailyRequestLimit(user.getEmail());
    }


    public void decrementDailyRequestCount(String username) {
        redisUtils.decrementDailyRequestCount(username);
    }
}


