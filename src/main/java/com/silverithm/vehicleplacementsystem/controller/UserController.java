package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.FindPasswordResponse;
import com.silverithm.vehicleplacementsystem.dto.PasswordChangeRequest;
import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyAddressDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyNameDTO;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PostMapping("api/v1/find/password")
    public ResponseEntity<FindPasswordResponse> findPassword(@RequestParam String email) {
        return ResponseEntity.ok().body(userService.findPassword(email));
    }

    @PostMapping("api/v1/change/password")
    public ResponseEntity<String> changePassword(@RequestBody PasswordChangeRequest passwordChangeRequest) {
        userService.changePassword(passwordChangeRequest);
        return ResponseEntity.ok().body("success");
    }


    @PostMapping("api/v1/logout")
    public ResponseEntity logout(HttpServletRequest request) {
        userService.logout(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("api/v1/users/company-name")
    public ResponseEntity<String> updateCompanyName(@AuthenticationPrincipal UserDetails userDetails,
                                                    @RequestBody UpdateCompanyNameDTO updateCompanyNameDTO) {
        userService.updateCompanyName(updateCompanyNameDTO, userDetails.getUsername());
        return ResponseEntity.ok().body("success");
    }

    @PutMapping("api/v1/users/company-address")
    public ResponseEntity<String> updateCompanyAddress(@AuthenticationPrincipal UserDetails userDetails,
                                                       @RequestBody UpdateCompanyAddressDTO updateCompanyAddressDTO)
            throws Exception {
        userService.updateCompanyAddress(updateCompanyAddressDTO, userDetails.getUsername());
        return ResponseEntity.ok().body("success");
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

    @GetMapping("/api/v1/users/dispatch-limit")
    public ResponseEntity<Integer> getDailyDispatchLimit(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().body(userService.getDailyDispatchLimit(userDetails.getUsername()));
    }

}
