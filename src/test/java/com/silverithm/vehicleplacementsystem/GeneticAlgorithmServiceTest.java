package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import com.silverithm.vehicleplacementsystem.entity.Chromosome;
import com.silverithm.vehicleplacementsystem.entity.DispatchType;
import com.silverithm.vehicleplacementsystem.service.GeneticAlgorithmService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GeneticAlgorithmServiceTest {


//    @Test
//    public void CalculateFitnessSuccessWithThreeFixedAssignment() {
//
//        //given
//        List<EmployeeDTO> employees = new ArrayList<>();
//        List<ElderlyDTO> elderly = new ArrayList<>();
//        Map<String, Map<String, Integer>> distanceMatrix = new HashMap<>();
//        List<FixedAssignmentsDTO> fixedAssignments = new ArrayList<>();
//        DispatchType dispatchType = DispatchType.IN;
//        Chromosome chromosome = new Chromosome();
//
//        GeneticAlgorithmService geneticAlgorithmService = new GeneticAlgorithmService(employees, elderly,
//                distanceMatrix, fixedAssignments, dispatchType);
//
//        //when
//        geneticAlgorithmService.calculateFitness(chromosome, employees, distanceMatrix,
//                geneticAlgorithmService.generateFixedAssignmentMap(fixedAssignments));
//        //then
//
//    }
}
