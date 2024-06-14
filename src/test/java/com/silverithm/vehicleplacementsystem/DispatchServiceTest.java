package com.silverithm.vehicleplacementsystem;

import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.service.DispatchService;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;


@SpringBootTest
public class DispatchServiceTest {

    @Autowired
    private DispatchService dispatchService;

    private final String key;

    public DispatchServiceTest(@Value("${tmap.key}") String key) {
        this.key = key;
    }

//    @Test
//    public void callTMapApi_WhenResultIsGreaterThanZero_Success() {
//        //given
//        Location location1 = new Location(37.4681241, 126.9377278);
//        Location location2 = new Location(37.4698515, 126.941749);
//
//        //when
//        int result = dispatchService.getDistanceTotalTimeWithTmapApi(location1, location2);
//        //then
//        assertThat(result).isGreaterThan(0);
//    }

//    static Stream<Location[]> locationProvider() {
//        return Stream.of(
//                new Location[]{new Location(0, 0), new Location(0, 0)},
//                new Location[]{new Location(37.4681241, 126.9377278), new Location(0, 0)},
//                new Location[]{new Location(0, 0), new Location(37.4698515, 126.941749)},
//                new Location[]{null, new Location(37.4698515, 126.941749)},
//                new Location[]{new Location(37.4681241, 126.9377278), null},
//                new Location[]{null, null}
//        );
//    }
//
//    @ParameterizedTest
//    @MethodSource("locationProvider")
//    public void callTMapApi_WhenEmptyLocation_ThrowException(Location location1, Location location2) {
//        // when & then
//        assertThatThrownBy(() -> dispatchService.getDistanceTotalTimeWithTmapApi(location1, location2))
//                .isInstanceOf(NullPointerException.class).hasMessageContaining("[ERROR] TMAP API 요청에 실패하였습니다.");
//    }


}
