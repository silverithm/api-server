package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public String refresh(String remoteUser) {
        return "";
    }

    public void delete(String username) {
    }

    public String signin(String username, String password) {
        return "";
    }

    public TokenInfo signup(UserDataDTO userDataDTO) {
        if (!userRepository.existsByUsername(userDataDTO.getName())) {
            AppUser user = new AppUser();
            user.setUsername(userDataDTO.getName());
            user.setEmail(userDataDTO.getEmail());
            user.setPassword(passwordEncoder.encode(userDataDTO.getPassword()));
            userRepository.save(user);

            return jwtTokenProvider.generateToken(userDataDTO.getName(), Collections.singleton(userDataDTO.getRole()));
        } else {
            throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
