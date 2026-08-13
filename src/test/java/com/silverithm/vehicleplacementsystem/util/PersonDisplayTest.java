package com.silverithm.vehicleplacementsystem.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonDisplayTest {

    @Test
    @DisplayName("직책이 있으면 이름 뒤에 괄호로 붙는다")
    void withPosition() {
        assertEquals("김하늘(요양보호사)", PersonDisplay.withPosition("김하늘", "요양보호사"));
        assertEquals("김도형(시설장)", PersonDisplay.withPosition("김도형", "시설장"));
    }

    @Test
    @DisplayName("직책이 없으면 예전처럼 이름만 나온다")
    void withoutPosition() {
        assertEquals("김하늘", PersonDisplay.withPosition("김하늘", null));
        assertEquals("김하늘", PersonDisplay.withPosition("김하늘", ""));
        assertEquals("김하늘", PersonDisplay.withPosition("김하늘", "   "));
    }

    @Test
    @DisplayName("앞뒤 공백은 정리한다")
    void trims() {
        assertEquals("김하늘(사무원)", PersonDisplay.withPosition(" 김하늘 ", " 사무원 "));
    }

    @Test
    @DisplayName("이름이 없어도 터지지 않는다")
    void nullName() {
        assertEquals("(시설장)", PersonDisplay.withPosition(null, "시설장"));
        assertEquals("", PersonDisplay.withPosition(null, null));
    }
}
