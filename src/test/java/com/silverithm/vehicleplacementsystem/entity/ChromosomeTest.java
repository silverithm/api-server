package com.silverithm.vehicleplacementsystem.entity;

import java.util.List;
import javax.swing.CellRendererPane;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class ChromosomeTest {

    private Chromosome chromosome = new Chromosome();

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


}
