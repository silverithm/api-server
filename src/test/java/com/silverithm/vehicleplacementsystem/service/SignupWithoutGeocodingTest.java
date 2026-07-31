package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 주소 좌표 변환(Geocoding) 제거 이후, 좌표 없이도 회사·어르신이 만들어지는지 확인한다.
 * 배차 서비스 종료로 좌표는 더 이상 쓰이지 않으며, 만료된 구글 API 키 때문에
 * 회원가입이 막히던 문제가 재발하지 않아야 한다.
 */
@DisplayName("좌표 없이 가입/등록 가능 여부 테스트")
class SignupWithoutGeocodingTest {

    @Test
    @DisplayName("좌표가 null이어도 회사가 생성된다")
    void companyCanBeCreatedWithoutCoordinates() {
        Company company = new Company("케어브이요양원", "경남 진주시 강남로 15", null);

        assertEquals("케어브이요양원", company.getName());
        assertEquals("경남 진주시 강남로 15", company.getAddressName());
        assertNull(company.getCompanyAddress());
    }

    @Test
    @DisplayName("좌표가 null이어도 회사 주소를 변경할 수 있다")
    void companyAddressCanBeUpdatedWithoutCoordinates() {
        Company company = new Company("케어브이요양원", "경남 진주시 강남로 15", null);

        company.updateAddress("서울특별시 관악구 신림동 1547-10", null);

        assertEquals("서울특별시 관악구 신림동 1547-10", company.getAddressName());
        assertNull(company.getCompanyAddress());
    }

    @Test
    @DisplayName("좌표가 null이어도 어르신이 등록된다")
    void elderCanBeCreatedWithoutCoordinates() {
        Company company = new Company("케어브이요양원", "경남 진주시 강남로 15", null);
        Elderly elderly = new Elderly("박어르신", "경남 진주시 대안동 1", null, false, company);

        assertEquals("박어르신", elderly.getName());
        assertNull(elderly.getHomeAddress());
    }

    @Test
    @DisplayName("코드 어디에도 GeocodingService 참조가 남아 있지 않다")
    void geocodingServiceIsFullyRemoved() {
        boolean stillPresent = Arrays.stream(new String[]{
                        "com.silverithm.vehicleplacementsystem.service.GeocodingService"
                })
                .anyMatch(name -> {
                    try {
                        Class.forName(name);
                        return true;
                    } catch (ClassNotFoundException e) {
                        return false;
                    }
                });

        assertFalse(stillPresent, "GeocodingService가 아직 클래스패스에 존재합니다");
    }
}
