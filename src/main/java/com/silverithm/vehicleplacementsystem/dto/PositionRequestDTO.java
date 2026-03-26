package com.silverithm.vehicleplacementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionRequestDTO {

    @NotBlank(message = "직책명을 입력해주세요")
    @Size(max = 100, message = "직책명은 100자를 초과할 수 없습니다")
    private String name;

    @Size(max = 255, message = "설명은 255자를 초과할 수 없습니다")
    private String description;

    private String memberRole;

    private Integer sortOrder;
}
