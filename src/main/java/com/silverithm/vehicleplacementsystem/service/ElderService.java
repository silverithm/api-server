package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.entity.Elder;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElderService {


    private final ElderRepository elderRepository;

    public void addOrder(AddElderRequest addElderRequest) {
        Elder elder = new Elder(addElderRequest);
        elderRepository.save(elder);
    }
}
