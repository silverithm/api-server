package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.entity.Elderly;
import com.silverithm.vehicleplacementsystem.repository.ElderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElderService {
    private final ElderRepository elderRepository;

    public void addElder(AddElderRequest addElderRequest) {
        Elderly elderly = new Elderly(addElderRequest.homeAddress(), addElderRequest.requireFrontSeat());
        elderRepository.save(elderly);
    }
}
