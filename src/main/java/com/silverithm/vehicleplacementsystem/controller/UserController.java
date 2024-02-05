package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signin")
    public TokenInfo login(@RequestBody UserSigninDTO userSigninDTO) {
        return userService.signin(userSigninDTO);
    }

    @PostMapping("/signup")
    public TokenInfo signup(@RequestBody UserDataDTO userDataDTO) {
        return userService.signup(userDataDTO);
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
//    @GetMapping("/refresh")
//    public String refresh(HttpServletRequest req) {
//        return userService.refresh(req.getRemoteUser());
//    }


}
