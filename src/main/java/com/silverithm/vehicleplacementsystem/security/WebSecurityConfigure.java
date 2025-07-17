package com.silverithm.vehicleplacementsystem.security;

import com.silverithm.vehicleplacementsystem.config.redis.RedisUtils;
import com.silverithm.vehicleplacementsystem.entity.AppUser;
import com.silverithm.vehicleplacementsystem.exception.CustomException;
import com.silverithm.vehicleplacementsystem.jwt.JwtAuthenticationFilter;
import com.silverithm.vehicleplacementsystem.jwt.JwtTokenProvider;
import com.silverithm.vehicleplacementsystem.repository.UserRepository;
import com.silverithm.vehicleplacementsystem.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
public class WebSecurityConfigure {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserRepository userRepository;

    //  CORS 설정
    CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedHeaders(Collections.singletonList("*"));
            config.setAllowedMethods(Collections.singletonList("*"));
            config.setAllowedOrigins(Arrays.asList(
                    "http://localhost:3000",
                    "https://silverithm.netlify.app",
                    "https://www.silverithm.co.kr",
                    "https://carev.netlify.app",
                    "https://www.carev.kr",
                    "https://carev.kr"
            ));
            config.setAllowCredentials(true);
            return config;
        };
    }

    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return new AuthenticationManager() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String email = authentication.getName();
                String password = authentication.getCredentials().toString();

                AppUser user = userRepository.findActiveByEmail(email)
                        .orElseThrow(() -> new CustomException("User not found", HttpStatus.UNPROCESSABLE_ENTITY));

                if (passwordEncoder().matches(password, user.getPassword())) {
                    return new UsernamePasswordAuthenticationToken(email, password);
                } else {
                    throw new AuthenticationException("Invalid username/password supplied") {
                    };
                }
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(corsConfigurer -> corsConfigurer.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                                .requestMatchers("/*").permitAll()
                                .requestMatchers("/metrics/*").permitAll()
                                .requestMatchers("/actuator/*").permitAll()
                                .requestMatchers("/h2-console/*").permitAll()
                                .requestMatchers("api/v1/signin").permitAll()
                                .requestMatchers("api/v1/find/password").permitAll()
                                .requestMatchers("/api/v1/employee/downloadEmployeeExcel").permitAll()
                                .requestMatchers("/api/v1/employee/downloadElderlyExcel").permitAll()
                                .requestMatchers("/api/v1/employee/uploadElderlyExcel").permitAll()
                                .requestMatchers("/api/v1/employee/uploadEmployeeExcel").permitAll()
                                .requestMatchers("api/v1/signup").permitAll()
                                .requestMatchers("api/v1/refresh-token").permitAll()
                                .requestMatchers("/api/v1/members/companies").permitAll()
                                .requestMatchers("/api/v1/members/signin").permitAll()
                                .requestMatchers("/api/v1/members/join-request").permitAll()
                                .requestMatchers("/api/v1/members/{id}/fcm-token").permitAll()
                                .requestMatchers("/api/v1/members/find/password").permitAll()
                                .requestMatchers("/api/v1/validate-token").permitAll()
                                .requestMatchers("/api/v1/app-version").permitAll()
                                .anyRequest().authenticated()
                ).addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisUtils),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


}