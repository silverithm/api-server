package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @GetMapping("api/v1/signin")
    public String healthCheck() {
        return "success";
    }

    @PostMapping("api/v1/signin")
    public SigninResponseDTO login(@RequestBody UserSigninDTO userSigninDTO) {
        return userService.signin(userSigninDTO);
    }

    @PostMapping("api/v1/signup")
    public TokenInfo signup(@RequestBody UserDataDTO userDataDTO) throws Exception {
        return userService.signup(userDataDTO);
    }


    @PostMapping("api/v1/logout")
    public ResponseEntity logout(HttpServletRequest request) {
        userService.logout(request);
        return ResponseEntity.ok().build();
    }

//    @DeleteMapping(value = "/{username}")
//    public String delete(@PathVariable String username) {
//        userService.delete(username);
//        return username;
//    }
//
//    @GetMapping(value = "/{username}")
//    public UserResponseDTO search(@PathVariable String username) {
//        return new UserResponseDTO(username);
//    }
//
//    @GetMapping(value = "/me")
//    public UserResponseDTO whoami(HttpServletRequest req) {
//        return new UserResponseDTO(req.getRemoteUser());
//    }
//
    @GetMapping("/refresh")
    public String refresh(HttpServletRequest req) {
        return userService.refresh(req.getRemoteUser());
    }


}
