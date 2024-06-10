package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.CompanyDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.entity.LinkDistance;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import com.silverithm.vehicleplacementsystem.service.GeneticAlgorithmService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
public class GeneticAlgorithmServiceTest {

    private GeneticAlgorithmService geneticAlgorithmService;

    @Test
    public void generateInitialPopulation_IfEmployeesNull_ThrowsIllegalArgumentException() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>();
        List<ElderlyDTO> elderlys = new ArrayList<>(List.of(new ElderlyDTO(2L, "", new Location(), false)));
        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
                new CompanyDTO(new Location()));
        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
        DispatchType distanceType = DispatchType.IN;

        //when
        geneticAlgorithmService = new GeneticAlgorithmService(employees, elderlys, distanceMatrix, fixedAssignments,
                distanceType);

        //then
        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());

    }

    @Test
    public void generateInitialPopulation_IfElderlysNull_ThrowsIllegalArgumentException() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>(
                List.of(new EmployeeDTO(1L, "", "", "", new Location(), new Location(), 4, false)));
        List<ElderlyDTO> elderlys = new ArrayList<>();
        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
                new CompanyDTO(new Location()));
        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
        DispatchType distanceType = DispatchType.IN;

        //when
        geneticAlgorithmService = new GeneticAlgorithmService(employees, elderlys, distanceMatrix, fixedAssignments,
                distanceType);

        //then
        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());

    }


    @Test
    public void generateInitialPopulation_IfElderlysAndEmployeesNull_ThrowsIllegalArgumentException() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>();
        List<ElderlyDTO> elderlys = new ArrayList<>();
        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
                new CompanyDTO(new Location()));
        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
        DispatchType distanceType = DispatchType.IN;

        //when
        geneticAlgorithmService = new GeneticAlgorithmService(employees, elderlys, distanceMatrix, fixedAssignments,
                distanceType);

        //then
        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());

    }


    @Test
    public void generateInitialPopulation_IfElderlysMoreThanEmployeeMaximumCapacity_ThrowsIllegalArgumentException() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>(
                List.of(new EmployeeDTO(1L, "", "", "", new Location(), new Location(), 1, false)));
        List<ElderlyDTO> elderlys = new ArrayList<>(
                List.of(new ElderlyDTO(2L, "", new Location(), false), new ElderlyDTO(3L, "", new Location(), false)));
        Map<String, Map<String, Integer>> distanceMatrix = generateTestDistanceMatrix(employees, elderlys,
                new CompanyDTO(new Location()));
        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
        DispatchType distanceType = DispatchType.IN;

        //when
        geneticAlgorithmService = new GeneticAlgorithmService(employees, elderlys, distanceMatrix, fixedAssignments,
                distanceType);

        //then
        Assertions.assertThrows(Exception.class, () -> geneticAlgorithmService.run());

    }


    Map<String, Map<String, Integer>> generateTestDistanceMatrix(List<EmployeeDTO> employees,
                                                                 List<ElderlyDTO> elderlys,
                                                                 CompanyDTO company) {
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

            distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
            distanceMatrix.get(destinationNodeId).put(startNodeId, 10);


        }

        for (int i = 0; i < elderlys.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {
                if (i == j) {
                    continue;
                }

                String startNodeId = "Elderly_" + elderlys.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
                distanceMatrix.get(destinationNodeId).put(startNodeId, 10);


            }

        }

        for (int i = 0; i < employees.size(); i++) {
            for (int j = 0; j < elderlys.size(); j++) {

                String startNodeId = "Employee_" + employees.get(i).id();
                String destinationNodeId = "Elderly_" + elderlys.get(j).id();

                distanceMatrix.get(startNodeId).put(destinationNodeId, 10);
                distanceMatrix.get(destinationNodeId).put(startNodeId, 10);


            }

        }

        return distanceMatrix;
    }


}
