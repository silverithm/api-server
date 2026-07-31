package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.entity.ChatRoom;
import com.silverithm.vehicleplacementsystem.repository.ChatRoomRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경로 변수에 실린 리소스 ID → 그 리소스가 속한 기관 ID.
 *
 * <p>{@code /rooms/{roomId}/...}처럼 리소스 ID만으로 접근하는 경로를
 * {@code CompanyScopeInterceptor}가 검증할 수 있도록 소속 기관을 돌려준다.
 */
@Service
@RequiredArgsConstructor
public class ResourceCompanyLookup {

    private final ChatRoomRepository chatRoomRepository;

    /** 채팅방의 소속 기관. 방이 없으면 비어 있다(존재 여부는 컨트롤러가 판단). */
    @Transactional(readOnly = true)
    public Optional<Long> chatRoomCompanyId(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .map(ChatRoom::getCompany)
                .map(company -> company.getId());
    }
}
