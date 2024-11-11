package com.silverithm.vehicleplacementsystem.service;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.dto.Location;
import com.silverithm.vehicleplacementsystem.dto.SigninResponseDTO;
import com.silverithm.vehicleplacementsystem.dto.UserResponseDTO.TokenInfo;
import com.silverithm.vehicleplacementsystem.dto.UserDataDTO;
import com.silverithm.vehicleplacementsystem.dto.UserSigninDTO;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.entity.Company;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Key;
import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService {

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private GeocodingService geocodingService;

    private Key secretKey;

    public UserService(@Value("${jwt.secretKey}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }


    public String refresh(String remoteUser) {
        return "";
    }

    public void delete(String username) {
    }

    @Transactional
    public SigninResponseDTO signin(UserSigninDTO userSigninDTO) {
        try {

            AppUser findUser = userRepository.findByEmail(userSigninDTO.getEmail());

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userSigninDTO.getEmail(), userSigninDTO.getPassword()));


            TokenInfo tokenInfo = jwtTokenProvider.generateToken(userSigninDTO.getEmail(),
                    Collections.singleton(findUser.getUserRole()));

            findUser.update(tokenInfo.getAccessToken(), tokenInfo.getRefreshToken());

            return new SigninResponseDTO(findUser.getId(), findUser.getUsername(), findUser.getCompanyName(), findUser.getCompanyAddress(), findUser.getCompanyAddressName(),
                    tokenInfo);


        } catch (AuthenticationException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public TokenInfo signup(UserDataDTO userDataDTO) throws Exception {

        TokenInfo tokenInfo = jwtTokenProvider.generateToken(userDataDTO.getName(),
                Collections.singleton(userDataDTO.getRole()));

        if (!userRepository.existsByEmail(userDataDTO.getEmail())) {
            Location companyLocation = geocodingService.getAddressCoordinates(userDataDTO.getCompanyAddress());

            System.out.println(userDataDTO.getName());
            AppUser user = new AppUser(userDataDTO.getName(), userDataDTO.getEmail(),
                    passwordEncoder.encode(userDataDTO.getPassword()), userDataDTO.getRole(),
                    tokenInfo.getAccessToken(), tokenInfo.getRefreshToken(),
                    userDataDTO.getCompanyName(), companyLocation, userDataDTO.getCompanyAddress());

            userRepository.save(user);

            return tokenInfo;
        } else {
            throw new CustomException("Useremail is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Transactional
    public void logout(HttpServletRequest request) {

        String accessToken = jwtTokenProvider.resolveToken(request);
        //Access Token 검증
        if (!jwtTokenProvider.validateToken(accessToken)) {
        }

        Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(accessToken)
                .getBody();

        String userEmail = claims.getSubject();
        long time = claims.getExpiration().getTime() - System.currentTimeMillis();

        //Access Token blacklist에 등록하여 만료시키기
        //해당 엑세스 토큰의 남은 유효시간을 얻음
        redisUtils.setBlackList(accessToken, userEmail, time);
        //DB에 저장된 Refresh Token 제거
//        refreshTokenRepository.deleteById(userEmail);

        AppUser findUser = userRepository.findByUsername(userEmail);
        findUser.updateRefreshToken(null);
    }

    public AppUser loadUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}

