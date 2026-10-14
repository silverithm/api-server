package com.silverithm.vehicleplacementsystem.lab.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silverithm.vehicleplacementsystem.lab.dto.LabCopayBillDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 비급여 항목·평가 점수처럼 JSON 문자열 컬럼에 담는 값의 변환. 읽기 실패는 빈 값으로 흘려 목록 전체가 죽지 않게 한다. */
final class LabJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LabJson() {
    }

    static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    static List<LabCopayBillDto.NonCoveredItem> readItems(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LabCopayBillDto.NonCoveredItem>>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    static Map<String, Integer> readScores(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Integer>>() {
            });
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }
}
