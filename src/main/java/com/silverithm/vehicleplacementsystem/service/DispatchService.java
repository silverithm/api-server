package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.DispatchLocationsDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import java.util.HashMap;
import java.util.Map;
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
    public static int INF = 987654321;
    public List<List<Location>> combinations = new ArrayList<>();
    int visited[] = new int[INF];

    private String apiId;
    private String apiSecret;

    public DispatchService(@Value("${naver.id}") String id, @Value("${naver.secret}") String secret) {
        apiId = id;
        apiSecret = secret;
    }

    public void callNaverMApAPI(double originLng, double originLat,
                                double destLng,
                                double destLat) {

        String start = originLng + "," + originLat;
        String goal = destLat + "," + destLng;
        String option = "trafast";

        // API 요청에 필요한 헤더 및 파라미터 값을 설정합니다.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-NCP-APIGW-API-KEY-ID", apiId);
        headers.set("X-NCP-APIGW-API-KEY", apiSecret);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 출발지, 목적지, 탐색 옵션을 맵에 추가합니다.
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("start", start);
        uriVariables.put("goal", goal);
        uriVariables.put("option", option);

        // RestTemplate을 생성합니다.
        RestTemplate restTemplate = new RestTemplate();

        // API를 호출하고 응답을 받습니다.
        ResponseEntity<String> response = restTemplate.exchange(
                "https://naveropenapi.apigw.ntruss.com/map-direction/v1/driving?start={start}&goal={goal}&option={option}",
                HttpMethod.GET,
                entity,
                String.class,
                uriVariables
        );

        // 응답을 출력합니다.
        System.out.println("Response status code: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody());

    }

    public List<Location> findClosestLocations(DispatchLocationsDTO dispatchLocationsDTO) {

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

        // 각 직원 가장 가까운 elderly를 찾는다.
        // 모든직원이 가장 가까운 순으로 찾고, 직원들에게 가장 가까운 노인분들을 붙여주고 또
        // 알고리즘 1의 문제점
        // 모든 결과를 조합하고 나중에 결과를 찾는 알고리즘 2와 달리 알고리즘 1은 매 계산 마다 가장 짧은 거리를 지정한다. 그럼 나머지 직원들은? 한 직원에게 결과를 몰아주면 다른 직원들은 제대로 배치를 받을 수 없음
        // 이거를 해결하기 위해서는 모든 직원들과 모든 노인분들과의 거리를 찾은 다음, 순서대로 배치하고, 또 찾은 노인분의 거리에서 또 가장 가까운 거리들을 찾은 다음 순서대로 배정함, 이렇게 되면 ..
        // 직원분들이 위치가 어느정도 겹친 상황이라면?
        // 노인 위치 5개가 있다.
        // 직원 위치 5개가 있다.
        // 가장 짧은 순으로 지정해 주면
        // 뒤로 배치받는 직원일수록 멀어지는 배치를 받을 수 밖에 없음 즉 평균적으로 최단거리를 맞추어야하는데 맨처음 배치받는 직원일수록 이득, 뒷사람은 그렇지 않음

        List<Location> elderlyLocations = dispatchLocationsDTO.getElderlyLocations();
        List<Location> employeeLocation = dispatchLocationsDTO.getEmployeeLocations();

        elderlyLocations.add(new Location(10, 20));
        elderlyLocations.add(new Location(15, 20));
        elderlyLocations.add(new Location(30, 20));
        elderlyLocations.add(new Location(40, 20));
        elderlyLocations.add(new Location(50, 20));
        elderlyLocations.add(new Location(60, 20));
        elderlyLocations.add(new Location(70, 20));
        elderlyLocations.add(new Location(80, 20));

        employeeLocation.add(new Location(10, 10));
        employeeLocation.add(new Location(20, 10));
        employeeLocation.add(new Location(30, 10));

        //employeeLocation 각각 가장 가까운 노인분 배치
        //employee와 elderly 차례대로 거리 비교
        //가장 짧고 employee의 앞자리가 false일 때 앞자리가 true인 노인이 있으면 넣어줌
        //가장 짧고 employee의 앞자리가 true이면 true인 노인이 있으면 패스함

        for (int i = 0; i < employeeLocation.size(); i++) {

            for (int j = 0; j < elderlyLocations.size(); j++) {
                if (visited[j] == 0) {
                    visited[j] = 1;

                    callNaverMApAPI(employeeLocation.get(i).getX(), employeeLocation.get(i).getY(),
                            elderlyLocations.get(j).getX(), elderlyLocations.get(j).getY());

                }
            }


        }

        return null;
    }


}
