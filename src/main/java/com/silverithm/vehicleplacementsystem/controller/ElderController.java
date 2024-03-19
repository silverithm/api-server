package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/api/v1/elders")
    public List<ElderlyDTO> getElders() {
        return elderService.getElders();
    }


}
