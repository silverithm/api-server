//package com.silverithm.vehicleplacementsystem;
//
//import com.silverithm.vehicleplacementsystem.dto.CompanyDTO;
//import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
//import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
//import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
//import com.silverithm.vehicleplacementsystem.dto.Location;
//import com.silverithm.vehicleplacementsystem.entity.DispatchType;
//import com.silverithm.vehicleplacementsystem.service.GeneticAlgorithm;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Assertions;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.EnumSource;
//import org.springframework.boot.test.context.SpringBootTest;
//
//@Slf4j
//@SpringBootTest
//public class GeneticAlgorithmServiceTest {
//
//    private GeneticAlgorithm geneticAlgorithmService;
//
//    @ParameterizedTest
//    @EnumSource(DispatchType.class)
//    public void generateInitialPopulation_IfEmployeesNull_ThrowsIllegalArgumentException(DispatchType dispatchType) {
//        //given
//        List<EmployeeDTO> employees = new ArrayList<>();
//        List<ElderlyDTO> elderlys = new ArrayList<>(List.of(new ElderlyDTO(2L, "", new Location(), false)));
//        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
//                new CompanyDTO(new Location()));
//        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
//
//        //when
//        geneticAlgorithmService = new GeneticAlgorithm(employees, elderlys, distanceMatrix, fixedAssignments,
//                dispatchType,"");
//
//        //then
//        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());
//
//    }
//
//    @ParameterizedTest
//    @EnumSource(DispatchType.class)
//    public void generateInitialPopulation_IfElderlysNull_ThrowsIllegalArgumentException(DispatchType dispatchType) {
//        //given
//        List<EmployeeDTO> employees = new ArrayList<>(
//                List.of(new EmployeeDTO(1L, "", "", "", new Location(), new Location(), 4, false)));
//        List<ElderlyDTO> elderlys = new ArrayList<>();
//        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
//                new CompanyDTO(new Location()));
//        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
//
//        //when
//        geneticAlgorithmService = new GeneticAlgorithm(employees, elderlys, distanceMatrix, fixedAssignments,
//                dispatchType,"");
//
//        //then
//        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());
//
//    }
//
//
//    @ParameterizedTest
//    @EnumSource(DispatchType.class)
//    public void generateInitialPopulation_IfElderlysAndEmployeesNull_ThrowsIllegalArgumentException(DispatchType dispatchType) {
//        //given
//        List<EmployeeDTO> employees = new ArrayList<>();
//        List<ElderlyDTO> elderlys = new ArrayList<>();
//        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
//                new CompanyDTO(new Location()));
//        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
//
//        //when
//        geneticAlgorithmService = new GeneticAlgorithm(employees, elderlys, distanceMatrix, fixedAssignments,
//                dispatchType,"");
//
//        //then
//        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());
//
//    }
//
//
//    @ParameterizedTest
//    @EnumSource(DispatchType.class)
//    public void generateInitialPopulation_IfElderlysMoreThanEmployeeMaximumCapacity_ThrowsIllegalArgumentException(DispatchType dispatchType) {
//        //given
//        List<EmployeeDTO> employees = new ArrayList<>(
//                List.of(new EmployeeDTO(1L, "", "", "", new Location(), new Location(), 1, false)));
//        List<ElderlyDTO> elderlys = new ArrayList<>(
//                List.of(new ElderlyDTO(2L, "", new Location(), false), new ElderlyDTO(3L, "", new Location(), false)));
//        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
//                new CompanyDTO(new Location()));
//        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
//
//        //when
//        geneticAlgorithmService = new GeneticAlgorithm(employees, elderlys, distanceMatrix, fixedAssignments,
//                dispatchType,"");
//
//        //then
//        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());
//
//    }
//
//
//    Map<String, Map<String, Integer>> generateTestDistanceMatrix(List<EmployeeDTO> employees,
//                                                                 List<ElderlyDTO> elderlys,
//                                                                 CompanyDTO company) {
//        Map<String, Map<String, Integer>> distanceMatrix = new HashMap<>();
//
//        distanceMatrix.put("Company", new HashMap<>());
//
//        for (EmployeeDTO employee : employees) {
//            distanceMatrix.put("Employee_" + employee.id(), new HashMap<>());
//        }
//
//        for (ElderlyDTO elderly : elderlys) {
//            distanceMatrix.put("Elderly_" + elderly.id(), new HashMap<>());
//        }
//
//        for (int i = 0; i < elderlys.size(); i++) {
//
//            String startNodeId = "Company";
//            String destinationNodeId = "Elderly_" + elderlys.get(i).id();
//
//            distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
//            distanceMatrix.get(destinationNodeId).put(startNodeId, 10);
//
//
//        }
//
//        for (int i = 0; i < elderlys.size(); i++) {
//            for (int j = 0; j < elderlys.size(); j++) {
//                if (i == j) {
//                    continue;
//                }
//
//                String startNodeId = "Elderly_" + elderlys.get(i).id();
//                String destinationNodeId = "Elderly_" + elderlys.get(j).id();
//
//                distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
//                distanceMatrix.get(destinationNodeId).put(startNodeId, 10);
//
//
//            }
//
//        }
//
//        for (int i = 0; i < employees.size(); i++) {
//            for (int j = 0; j < elderlys.size(); j++) {
//
//                String startNodeId = "Employee_" + employees.get(i).id();
//                String destinationNodeId = "Elderly_" + elderlys.get(j).id();
//
//                distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
//                distanceMatrix.get(destinationNodeId).put(startNodeId, 10);
//
//
//            }
//
//        }
//
//        return distanceMatrix;
//    }
//
//
//}
