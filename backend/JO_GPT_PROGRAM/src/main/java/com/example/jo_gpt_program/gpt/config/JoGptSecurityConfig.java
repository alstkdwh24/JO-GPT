package com.example.jo_gpt_program.gpt.config;

import com.example.jo_gpt_program.gpt.config.filter.JwtDelegateFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

// jo-gpt-program 자체 Security 설정
@Configuration
@EnableWebSecurity
public class JoGptSecurityConfig {

    @Value("${spring.memberSecurity.url:https://agentcloudllm.me}")
    private String memberSecurityUrl;

    @Value("${spring.joGptProgram.url:https://agentcloudllm.me}")
    private String joGptProgramUrl;

    @Value("${spring.frontend.url:https://agentcloudllm.me}")
    private String frontendUrl;

    private final JwtDelegateFilter jwtDelegateFilter;

    public JoGptSecurityConfig(JwtDelegateFilter jwtDelegateFilter) {
        this.jwtDelegateFilter = jwtDelegateFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(Arrays.asList(
                    memberSecurityUrl,
                    joGptProgramUrl,
                    frontendUrl,
                    "http://localhost:5173",
                    "http://agentcloudllm.me",
                    "https://agentcloudllm.me"));
            cors.setAllowedMethods(Collections.singletonList("*"));
            cors.setAllowedHeaders(Arrays.asList(
                    "Authorization", "Content-Type", "Cache-Control","Accept",
                    "X-Requested-With", "X-Model", "X-Custom-Prompt",
                    "X-NCP-APIGW-API-KEY-ID", "X-NCP-APIGW-API-KEY",
                    "Last-Event-ID"));
            cors.setAllowCredentials(true);
            cors.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));
            cors.setMaxAge(3600L);
            return cors;
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth


                        // ✅ /alert/** 제거 → 인증 필요!
                        .requestMatchers("/contents/**", "/auth/**", "/connect/**").permitAll()

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtDelegateFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
