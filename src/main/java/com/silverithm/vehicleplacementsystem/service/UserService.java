package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public String refresh(String remoteUser) {
        return "";
    }

    public void delete(String username) {
    }

    public TokenInfo signin(UserSigninDTO userSigninDTO) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userSigninDTO.getName(), userSigninDTO.getPassword()));
            return jwtTokenProvider.generateToken(userSigninDTO.getName(),
                    Collections.singleton(userRepository.findByUsername(userSigninDTO.getName()).getUserRole()));
        } catch (AuthenticationException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public TokenInfo signup(UserDataDTO userDataDTO) {

        if (!userRepository.existsByUsername(userDataDTO.getName())) {
            System.out.println(userDataDTO.getName());
            AppUser user = new AppUser();
            user.setUsername(userDataDTO.getName());
            user.setEmail(userDataDTO.getEmail());
            user.setPassword(passwordEncoder.encode(userDataDTO.getPassword()));
            user.setUserRole(userDataDTO.getRole());
            userRepository.save(user);

            return jwtTokenProvider.generateToken(userDataDTO.getName(), Collections.singleton(userDataDTO.getRole()));
        } else {
            throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
