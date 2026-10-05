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

    @Value("${spring.cors.dev-origin:}")
    private String devOrigin;

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
                    devOrigin, // 로컬 개발 때만 spring.cors.dev-origin=http://localhost:5173 (운영은 비워 둠 → 어떤 Origin과도 불일치)
                    "https://agentcloudllm.me"));
            cors.setAllowedMethods(Collections.singletonList("*"));
            cors.setAllowedHeaders(Arrays.asList(
                    "Authorization", "Content-Type", "Cache-Control","Accept",
                    "X-Requested-With", "X-Model", "X-Custom-Prompt",
                    "X-NCP-APIGW-API-KEY-ID", "X-NCP-APIGW-API-KEY",
                    "Last-Event-ID"));
            cors.setAllowCredentials(true);
            cors.setExposedHeaders(Collections.emptyList()); // Authorization을 JS에 공개하지 않음 (Set-Cookie는 원래 JS가 못 읽음)
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
                        // /contents/** 는 채팅·LLM 호출이라 인증 필요. 받은 메시지를 되돌려주기만 하는 알림만 공개
                        .requestMatchers("/contents/notifications").permitAll()
                        // /connect/** : [초기 버전] Google 계정 연결(Connect) API용 경로. 현재 컨트롤러는 제거됨
                        .requestMatchers("/auth/**", "/connect/**").permitAll()

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtDelegateFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
