package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.CoupleRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.CoupleUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderUpdateRequestDTO;
import com.silverithm.vehicleplacementsystem.dto.ElderlyDTO;
import com.silverithm.vehicleplacementsystem.service.CoupleService;
import java.util.List;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public ResponseEntity<List<CoupleResponseDTO>> getCouples(@PathVariable("userId") final Long userId) {
        return ResponseEntity.ok().body(coupleService.getCouples(userId));
    }

    @DeleteMapping("/api/v1/couple/{id}")
    public ResponseEntity<Long> deleteCouple(@PathVariable("id") final Long id) {
        return coupleService.deleteCouple(id);
    }

    @PutMapping("/api/v1/couple/{id}")
    public ResponseEntity<Long> updateCouple(@PathVariable("id") final Long id,
                                            @RequestBody CoupleUpdateRequestDTO coupleUpdateRequestDTO)
            throws Exception {
        return ResponseEntity.ok().body(coupleService.updateCouple(id, coupleUpdateRequestDTO));
    }


}
