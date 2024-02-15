package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.service.DispatchService;
import com.silverithm.vehicleplacementsystem.service.DispatchService.Elderly;
import com.silverithm.vehicleplacementsystem.service.DispatchService.GeneticAlgorithm.Employee;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DispatchServiceTest {

    private final DispatchService dispatchService = new DispatchService();

    @Test
    public void dispatchServiceTest() {

        List<Employee> employees = new ArrayList<>();
        List<Elderly> elderly = new ArrayList<>();
        int requiredFrontSeat = employees.size();

        dispatchService.getOptimizedAssignments(employees, elderly, requiredFrontSeat);

    }

}
