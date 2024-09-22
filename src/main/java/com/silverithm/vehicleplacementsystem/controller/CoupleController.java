package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.service.CoupleService;
import java.util.List;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CoupleController {

    private final CoupleService coupleService;

    public CoupleController(CoupleService coupleService) {
        this.coupleService = coupleService;
    }

    @PostMapping("/api/v1/couple/{userId}")
    public ResponseEntity<Long> coupleAdd(@PathVariable("userId") final Long userId,
                                          @RequestBody CoupleRequestDTO coupleRequestDTO)
            throws Exception {
        return ResponseEntity.ok().body(coupleService.addCouple(userId, coupleRequestDTO));
    }

    @GetMapping("/api/v1/couple/{userId}")
    public ResponseEntity<List<CoupleResponseDTO>> getElders(@PathVariable("userId") final Long userId) {
        return ResponseEntity.ok().body(coupleService.getCouples(userId));
    }


}
