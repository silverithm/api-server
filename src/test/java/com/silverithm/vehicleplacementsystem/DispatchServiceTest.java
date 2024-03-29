package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.entity.Employee;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import com.silverithm.vehicleplacementsystem.service.DispatchService.GeneticAlgorithm;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
public class DispatchServiceTest {

//    @Autowired
//    private DispatchService dispatchService;
//
//    @Test
//    public void dispatchServiceTest() {
//        List<Employee> employees = new ArrayList<>();
//        List<Elderly> elderly = new ArrayList<>();
//        int requiredFrontSeat = employees.size();
////        dispatchService.getOptimizedAssignments(employees, elderly, requiredFrontSeat);
//    }
//
//    @Test
//    public void tMapApiTest() {
//        //given
//        int totalTime = 0;
//        //when
//        totalTime = dispatchService.callTMapAPI(new Location(37.36520202, 127.10323758),
//                new Location(35.17240084, 128.87264091));
//        //then
//        assertThat(totalTime).isNotZero();
//
//    }
//
//    @Test
//    public void crossoverTest() {
//        //given
//        //when
//
//        //then
//
//    }

}
