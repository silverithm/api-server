package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.FixedAssignmentsDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class FixedAssignments {

    private final Map<Integer, List<Integer>> fixedAssignments;

    public FixedAssignments(List<FixedAssignmentsDTO> fixedAssignmentDtos, List<EmployeeDTO> employees,
                            List<ElderlyDTO> elderlys) {
        fixedAssignments = generateFixedAssignment(fixedAssignmentDtos, employees, elderlys);
    }

    private Map<Integer, List<Integer>> generateFixedAssignment(List<FixedAssignmentsDTO> fixedAssignmentsDtos,
                                                                List<EmployeeDTO> employees,
                                                                List<ElderlyDTO> elderlys) {

        Map<Integer, List<Integer>> fixedAssignments = new HashMap<>();

        if (fixedAssignmentsDtos == null) {
            return fixedAssignments;
        }

        for (FixedAssignmentsDTO fixedAssignment : fixedAssignmentsDtos) {
            long employeeId = fixedAssignment.employee_id();
            long elderlyId = fixedAssignment.elderly_id();
            int sequence = fixedAssignment.sequence();

            int employee_idx = employees.stream().map((employee) -> employee.id()).collect(Collectors.toList())
                    .indexOf(employeeId);
            int elderly_idx = elderlys.stream().map((elderly) -> elderly.id()).collect(Collectors.toList())
                    .indexOf(elderlyId);

            if (fixedAssignments.get(employee_idx) == null && sequence > 0) {
                List<Integer> createdList = new ArrayList<>();

                for (int i = 0; i < employees.get(employee_idx).maximumCapacity(); i++) {
                    createdList.add(-1);
                }

                createdList.set(sequence - 1, (int) elderly_idx);
                fixedAssignments.put(employee_idx, createdList);
            } else if (sequence > 0) {
                List<Integer> prevList = fixedAssignments.get(employee_idx);
                prevList.set(sequence - 1, (int) elderly_idx);
                fixedAssignments.put(employee_idx, prevList);
            }

        }
        return fixedAssignments;
    }


    public double evaluateFitness(Chromosome chromosome, double fitness) {

        for (int employee_idx : fixedAssignments.keySet()) {
            for (int i = 0; i < chromosome.getGenes().get(employee_idx).size(); i++) {
                if (chromosome.getGenes().get(employee_idx).get(i) != fixedAssignments.get(employee_idx).get(i)
                        && fixedAssignments.get(employee_idx).get(i) != -1) {
                    fitness = 0.0;
                    break;
                }
            }
            if (fitness == 0.0) {
                break;
            }
        }

        return fitness;
    }
}
