package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ElderController {

    private final ElderService elderService;

    @PostMapping("/api/v1/elder")
    public String elderAdd(@RequestBody AddElderRequest addElderRequest) {
        elderService.addElder(addElderRequest);
        return "Success";
    }

}
