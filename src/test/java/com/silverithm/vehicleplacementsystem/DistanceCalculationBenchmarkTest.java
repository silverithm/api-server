//package com.silverithm.vehicleplacementsystem;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import com.silverithm.vehicleplacementsystem.dto.Location;
//import com.silverithm.vehicleplacementsystem.dto.OsrmApiResponseDTO;
//import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
//import com.silverithm.vehicleplacementsystem.repository.LinkDistanceRepository;
//import com.silverithm.vehicleplacementsystem.service.DispatchServiceV3;
//import java.util.Optional;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.annotation.Profile;
//
//@SpringBootTest
//public class DistanceCalculationBenchmarkTest {
//
//    @Autowired
//    private LinkDistanceRepository linkDistanceRepository;
//    @Autowired
//    private DispatchServiceV3 dispatchServiceV3;
//
//    @Test
//    @DisplayName("거리 계산 성능 비교 테스트")
//    void compareDistanceCalculationPerformance() {
//        // Test data
//        String startNodeId = "Elderly_1";
//        String destinationNodeId = "Company";
//        Location companyAddress = new Location(35.1899492, 35.1899492);
//        Location elderlyAddress = new Location(35.1899492, 35.1899492);
//
//        int iterations = 100;
//        long dbTotalTime = 0;
//        long apiTotalTime = 0;
//
//        // Warm up
//        for (int i = 0; i < 10; i++) {
//            linkDistanceRepository.findNodeByStartNodeIdAndDestinationNodeId(startNodeId, destinationNodeId);
//            dispatchServiceV3.getDistanceTotalTimeWithOsrmApi(companyAddress, elderlyAddress);
//        }
//
//        // Actual benchmark
//        for (int i = 0; i < iterations; i++) {
//            // DB Query timing
//            long dbStart = System.nanoTime();
//            Optional<LinkDistance> linkDistance = linkDistanceRepository.findNodeByStartNodeIdAndDestinationNodeId(
//                    startNodeId, destinationNodeId);
//            dbTotalTime += System.nanoTime() - dbStart;
//
//            // API Call timing
//            long apiStart = System.nanoTime();
//            OsrmApiResponseDTO osrmApiResponseDTO = dispatchServiceV3.getDistanceTotalTimeWithOsrmApi(
//                    companyAddress, elderlyAddress);
//            apiTotalTime += System.nanoTime() - apiStart;
//        }
//
//        double dbAvgTime = (double) dbTotalTime / iterations / 1_000_000; // Convert to milliseconds
//        double apiAvgTime = (double) apiTotalTime / iterations / 1_000_000;
//
//        System.out.printf("Database Query Average Time: %.2f ms%n", dbAvgTime);
//        System.out.printf("OSRM API Call Average Time: %.2f ms%n", apiAvgTime);
//        System.out.printf("Performance Difference: %.2f times%n", apiAvgTime / dbAvgTime);
//
//        // Optional: Assert performance expectations
//        assertTrue(dbAvgTime < apiAvgTime, "Database query should be faster than API call");
//    }
//}