package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.entity.Elder;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ElderController {

    private final ElderService elderService;

    @PostMapping("/api/v1/elder")
    public void elderAdd() {
        elderService.addOrder();
    }

}
