package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.AddElderRequest;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.service.ElderService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ElderController {

    private final ElderService elderService;

    @PostMapping("/api/v1/elder/{userId}")
    public String elderAdd(@PathVariable("userId") final Long userId, @RequestBody AddElderRequest addElderRequest)
            throws Exception {
        elderService.addElder(userId, addElderRequest);
        return "Success";
    }

    @GetMapping("/api/v1/elders/{userId}")
    public List<ElderlyDTO> getElders(@PathVariable("userId") final Long userId) {
        return elderService.getElders(userId);
    }

    @DeleteMapping("/api/v1/elder/{id}")
    public String deleteElder(@PathVariable("id") final Long id) {
        elderService.deleteElder(id);
        return "Success";
    }

    @PutMapping("/api/v1/elder/{id}")
    public String updateElder(@PathVariable("id") final Long id,
                              @RequestBody ElderUpdateRequestDTO elderUpdateRequestDTO) throws Exception {
        elderService.updateElder(id, elderUpdateRequestDTO);
        return "Success";
    }

    @PutMapping("/api/v1/elder/frontseat/{id}")
    public String updateElderRequiredFrontSeat(@PathVariable("id") final Long id,
                                               @RequestBody ElderUpdateRequestDTO elderUpdateRequestDTO)
            throws Exception {
        elderService.updateElderRequiredFrontSeat(id, elderUpdateRequestDTO);
        return "Success";
    }
}
