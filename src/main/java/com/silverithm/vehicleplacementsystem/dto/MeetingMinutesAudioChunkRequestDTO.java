package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 녹음 조각 등록. 파일 자체는 /files/upload(category=meetings)로 먼저 올리고 경로만 넘긴다. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingMinutesAudioChunkRequestDTO {

    @NotNull
    private Integer seq;

    @NotBlank
    private String filePath;

    private Integer durationSec;
}
