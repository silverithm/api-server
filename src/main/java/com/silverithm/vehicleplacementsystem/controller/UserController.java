package com.silverithm.vehicleplacementsystem.controller;

import com.silverithm.vehicleplacementsystem.dto.FindPasswordResponse;
import com.silverithm.vehicleplacementsystem.dto.PasswordChangeRequest;
import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.SubscriptionResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.TokenRefreshRequest;
import com.silverithm.vehicleplacementsystem.dto.TokenResponse;
import com.silverithm.vehicleplacementsystem.dto.TokenValidationRequest;
import com.silverithm.vehicleplacementsystem.dto.TokenValidationResponse;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyAddressDTO;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyAddressResponse;
import com.silverithm.vehicleplacementsystem.dto.UpdateCompanyNameDTO;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserInfoResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;


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

    @DeleteMapping("api/v1/users")
    public ResponseEntity<String> deleteUser(@AuthenticationPrincipal UserDetails userDetails) {
        userService.deleteUser(userDetails.getUsername());
        return ResponseEntity.ok().body("success");
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
    public ResponseEntity<UpdateCompanyAddressResponse> updateCompanyAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateCompanyAddressDTO updateCompanyAddressDTO)
            throws Exception {

        return ResponseEntity.ok()
                .body(userService.updateCompanyAddress(updateCompanyAddressDTO, userDetails.getUsername()));
    }


    @PutMapping("api/v1/users/customer-key")
    public ResponseEntity<String> updateCustomerKey() {
        userService.updateCustomerKey();
        return ResponseEntity.ok().body("success");
    }


    @PostMapping("api/v1/refresh-token")
    public ResponseEntity<UserResponseDTO.TokenInfo> refreshToken(
            @Valid @RequestBody final TokenRefreshRequest tokenRefreshRequest) {
        return ResponseEntity.ok().body(userService.refreshToken(tokenRefreshRequest));
    }

    @GetMapping("api/v1/users/dispatch-limit")
    public ResponseEntity<Integer> getDailyDispatchLimit(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().body(userService.getDailyDispatchLimit(userDetails.getUsername()));
    }

    @GetMapping("api/v1/user/subscription")
    public ResponseEntity<SubscriptionResponseDTO> getUserSubscription(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().body(userService.getUserSubscription(userDetails.getUsername()));
    }

    /**
     * 토큰 검증 API (Request Body로 토큰 전달)
     * @param tokenValidationRequest 검증할 토큰이 포함된 요청
     * @return 토큰 검증 결과
     */
    @PostMapping("api/v1/validate-token")
    public ResponseEntity<TokenValidationResponse> validateToken(
            @Valid @RequestBody TokenValidationRequest tokenValidationRequest) {
        TokenValidationResponse response = userService.validateToken(tokenValidationRequest.getToken());
        
        // 토큰이 유효하지 않은 경우 400 Bad Request, 유효한 경우 200 OK
        if (!response.isValid()) {
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok().body(response);
    }

    /**
     * 토큰 검증 API (Authorization Header에서 토큰 추출)
     * @param request HTTP 요청 (Authorization Header에서 토큰 추출)
     * @return 토큰 검증 결과
     */
    @GetMapping("api/v1/validate-token")
    public ResponseEntity<TokenValidationResponse> validateTokenFromHeader(HttpServletRequest request) {
        TokenValidationResponse response = userService.validateTokenFromRequest(request);
        
        // 토큰이 유효하지 않은 경우 400 Bad Request, 유효한 경우 200 OK
        if (!response.isValid()) {
            return ResponseEntity.badRequest().body(response);
        }
        
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("api/v1/users/info")
    public ResponseEntity<UserInfoResponseDTO> getUserInfo(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok().body(userService.getUserInfo(userDetails.getUsername()));
    }


}
