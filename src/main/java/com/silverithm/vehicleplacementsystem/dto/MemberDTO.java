package com.silverithm.vehicleplacementsystem.dto;

import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberDTO {

    private Long id;
    private String username;
    private String name;
    private String email;
    private String phoneNumber;
    private String role;
    private String status;
    private String department;
    private String position;
    private Long positionId;
    private String profileImageUrl;
    private CompanyListDTO company;
    private List<String> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MemberDTO fromEntity(Member entity) {
        Set<String> perms = entity.getPermissions();
        return MemberDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .name(entity.getName())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .role(entity.getRole().name().toLowerCase())
                .status(entity.getStatus().name().toLowerCase())
                .department(entity.getDepartment())
                .position(entity.getPosition())
                .positionId(entity.getPositionEntity() != null ? entity.getPositionEntity().getId() : null)
                .profileImageUrl(entity.getProfileImageUrl())
                .company(entity.getCompany() != null ? CompanyListDTO.fromEntity(entity.getCompany()) : null)
                .permissions(perms != null ? List.copyOf(perms) : List.of())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 관리자(시설장) 계정을 직원 목록과 같은 모양으로 내려주기 위한 변환.
     * members 테이블 소속이 아니므로 role을 Member.Role과 겹치지 않는 "facility_admin"으로 둬
     * 프론트에서 직원과 구분할 수 있게 한다. id는 app_user PK를 그대로 쓰므로
     * members.id와 값이 겹칠 수 있다 — 화면에서는 role과 함께 키로 써야 한다.
     */
    public static MemberDTO fromAppUser(AppUser entity) {
        return MemberDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .name(entity.getUsername())
                .email(entity.getEmail())
                .phoneNumber(null)
                .role("facility_admin")
                .status("active")
                .department(null)
                .position(entity.getPosition())
                .positionId(entity.getPositionEntity() != null ? entity.getPositionEntity().getId() : null)
                .profileImageUrl(entity.getProfileImageUrl())
                .company(entity.getCompany() != null ? CompanyListDTO.fromEntity(entity.getCompany()) : null)
                .permissions(List.of())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getModifiedAt())
                .build();
    }
}