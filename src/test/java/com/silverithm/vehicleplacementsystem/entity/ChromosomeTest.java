package com.silverithm.vehicleplacementsystem.entity;

import com.silverithm.vehicleplacementsystem.dto.EmployeeDTO;
import com.silverithm.vehicleplacementsystem.dto.Location;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.CellRendererPane;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Before;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class ChromosomeTest {

    private Chromosome chromosome;

    @BeforeEach
    public void setUp() {
        chromosome = new Chromosome();
    }


    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    public void createRandomElderlyIndexs_isSizeEuqalToTotalElderly_Success(int totalElderly) {
        //given
        //when
        List<Integer> elderlyIndexs = chromosome.createRandomElderlyIndexs(totalElderly);
        //then
        Assertions.assertThat(elderlyIndexs.size()).isEqualTo(totalElderly);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    public void createRandomElderlyIndexs_isChromosomeRangedInTotalElderly_Success(int totalElderly) {
        //given
        //when
        List<Integer> elderlyIndexs = chromosome.createRandomElderlyIndexs(totalElderly);
        //then
        for (int i = 0; i < totalElderly; i++) {
            Assertions.assertThat(elderlyIndexs).contains(i);
        }
    }

    @Test
    public void initializeChromosomeWithMaximumCapacity_SizeEqualCapacity_Success() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>();
        employees.add(new EmployeeDTO(1L, "TEST1", new Location(), new Location(), 5));
        employees.add(new EmployeeDTO(2L, "TEST2", new Location(), new Location(), 3));
        employees.add(new EmployeeDTO(3L, "TEST3", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(4L, "TEST4", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(5L, "TEST5", new Location(), new Location(), 2));
        int numEmployee = 5;
        int[] employeesCapacityLeft = new int[5];
        //when
        List<List<Integer>> genes = chromosome.initializeChromosomeWithMaximumCapacity(employees);
        //then
        for (int i = 0; i < employees.size(); i++) {
            log.info(genes.get(i).toString());
            Assertions.assertThat(genes.get(i).size()).isEqualTo(employees.get(i).maximumCapacity());
        }
    }

    @Test
    public void initializeChromosomeWithMaximumCapacity_IsAllValueEqualMinus1_Success() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>();
        employees.add(new EmployeeDTO(1L, "TEST1", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(2L, "TEST2", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(3L, "TEST3", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(4L, "TEST4", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(5L, "TEST5", new Location(), new Location(), 5));

        //when
        List<List<Integer>> genes = chromosome.initializeChromosomeWithMaximumCapacity(employees);
        //then
        for (int i = 0; i < genes.size(); i++) {
            log.info(genes.get(i).toString());
            for (int j = 0; j < genes.get(i).size(); j++) {
                Assertions.assertThat(genes.get(i).get(j)).isEqualTo(-1);
            }
        }
    }

    @Test
    public void initializeEmployeesCapacityLeft_IsAllValueEqualCapacity_Success() {
        //given
        List<EmployeeDTO> employees = new ArrayList<>();
        employees.add(new EmployeeDTO(1L, "TEST1", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(2L, "TEST2", new Location(), new Location(), 6));
        employees.add(new EmployeeDTO(3L, "TEST3", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(4L, "TEST4", new Location(), new Location(), 4));
        employees.add(new EmployeeDTO(5L, "TEST5", new Location(), new Location(), 5));
        //when
        int[] employeesCapacityLeft = chromosome.initializeEmployeesCapacityLeft(employees);
        //then
        for (int i = 0; i < employees.size(); i++) {
            Assertions.assertThat(employeesCapacityLeft[i]).isEqualTo(employees.get(i).maximumCapacity());
        }
    }


}
