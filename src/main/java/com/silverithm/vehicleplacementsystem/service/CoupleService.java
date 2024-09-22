package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Couple;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.repository.CoupleRepository;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CoupleService {
    private final ElderRepository elderRepository;
    private final UserRepository userRepository;
    private final CoupleRepository coupleRepository;

    public CoupleService(ElderRepository elderRepository, UserRepository userRepository,
                         CoupleRepository coupleRepository) {
        this.elderRepository = elderRepository;
        this.userRepository = userRepository;
        this.coupleRepository = coupleRepository;
    }

    public Long addCouple(Long userId, CoupleRequestDTO coupleRequestDTO) throws NotFoundException {

        Elderly elder1 = elderRepository.findById(coupleRequestDTO.elderId1())
                .orElseThrow(() -> new NotFoundException());
        Elderly elder2 = elderRepository.findById(coupleRequestDTO.elderId2())
                .orElseThrow(() -> new NotFoundException());

        AppUser user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException());

        Couple couple = new Couple(user, elder1, elder2);

        return coupleRepository.save(couple).getId();
    }

    public List<CoupleResponseDTO> getCouples(Long userId) {
        List<Couple> couples = coupleRepository.findByUserId(userId);
        return CoupleResponseDTO.from(couples);
    }

    public ResponseEntity<Long> deleteCouple(Long id) {
        coupleRepository.deleteById(id);
        return ResponseEntity.ok().body(id);
    }

    @Transactional
    public Long updateCouple(Long id, CoupleUpdateRequestDTO coupleUpdateRequestDTO) throws NotFoundException {
        Couple couple = coupleRepository.findById(id).orElseThrow(() -> new NotFoundException());

        Elderly elder1 = elderRepository.findById(coupleUpdateRequestDTO.elderId1())
                .orElseThrow(() -> new NotFoundException());
        Elderly elder2 = elderRepository.findById(coupleUpdateRequestDTO.elderId2())
                .orElseThrow(() -> new NotFoundException());

        couple.update(elder1, elder2);

        System.out.println(couple.getElder1().getId() + " " + couple.getElder2().getId());

        return couple.getId();
    }
}
