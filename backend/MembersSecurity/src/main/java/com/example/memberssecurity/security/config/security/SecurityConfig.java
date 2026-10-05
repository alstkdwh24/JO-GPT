package com.example.memberssecurity.security.config.security;

import com.example.memberssecurity.member.service.LoginAttemptService;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.dto.CustomOAuth2UserService;
import com.example.memberssecurity.security.config.filter.ClientTypeFilter;
import com.example.memberssecurity.security.config.filter.JWTFilter;
import com.example.memberssecurity.security.config.filter.LoginFilter;
import com.example.memberssecurity.security.config.handler.OAuth2LoginSuccessHandler;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import com.example.memberssecurity.security.config.repository.RedisOAuth2AuthorizationRequestRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {
    private final RefreshTokenService refreshTokenService;

    private final AuthenticationConfiguration authenticationConfiguration;
    private final JWTUtils jwtUtils;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final TokenCookies tokenCookies;
    private final LoginAttemptService loginAttemptService;
    @Value("${spring.memberSecurity.url}")
    private String memberSecurityUrl;

    @Value("${spring.joGptProgram.url}")
    private String joGptProgramUrl;

    @Value("${spring.frontend.url}")
    private String frontendUrl;

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration corsConfiguration = new CorsConfiguration();

            corsConfiguration.setAllowedOrigins(Arrays.asList(
                    memberSecurityUrl,
                    joGptProgramUrl,
                    frontendUrl,
                    "https://agentcloudllm.me:5173",
                    "https://agentcloudllm.me"));

            corsConfiguration.setAllowedMethods(Collections.singletonList("*"));

            corsConfiguration.setAllowedHeaders(
                    Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-Requested-With", "X-Model",
                            "X-Custom-Prompt"));

            corsConfiguration.setAllowCredentials(true);
            corsConfiguration.setExposedHeaders(Collections.emptyList()); // Authorization을 JS에 공개하지 않음 (Set-Cookie는 원래 JS가 못 읽음)
            corsConfiguration.setMaxAge(3600L);
            return corsConfiguration;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService,
                                                   OAuth2LoginSuccessHandler successHandler, ClientRegistrationRepository clientRegistrationRepository)
            throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/login/**", "/login/oauth2/**", "/", "/signUp", "/home/**", "/css/**",
                                "/js/**", "/image/**", "/oauth2/**", "/joGpt/**", "/oauth2/authorization/**",
                                "/favicon.ico", "/error", "/auth/**", "/connect/**") // /connect/** : [초기 버전] Google 계정 연결(Connect) API용. 현재 컨트롤러는 제거됨
                        .permitAll()
                        .requestMatchers("/JO_GPT_PROGRAM/**", "/contents/**", "/gptApi/**").hasAuthority("ROLE_USER")
                        .requestMatchers("/admin").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())

                .addFilterBefore(new ClientTypeFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JWTFilter(jwtUtils, refreshTokenService, tokenCookies), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new LoginFilter(authenticationManager(), jwtUtils, refreshTokenService, tokenCookies, loginAttemptService), JWTFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .logout(logout -> logout.logoutUrl("/login/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            // access jti 블랙리스트 + Redis refresh 삭제 → 다른 서비스(/auth/validate)에서도 즉시 무효
                            refreshTokenService.revoke(TokenCookies.resolveAccessToken(request),
                                    TokenCookies.read(request, TokenCookies.REFRESH));
                            tokenCookies.clear(response);
                        })
                        .logoutSuccessHandler(
                                (request, response, authentication) -> response.setStatus(HttpServletResponse.SC_OK)))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(authorizationRequestRepository)
                                .authorizationRequestResolver(
                                        authorizationRequestResolver(clientRegistrationRepository)))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(successHandler)
                        .failureHandler((request, response, exception) -> {
                            String errorMessage = exception.getMessage();
                            String encodedMessage = java.net.URLEncoder.encode(errorMessage,
                                    java.nio.charset.StandardCharsets.UTF_8);
                            response.sendRedirect("http://localhost:5173?error=" + encodedMessage);
                        }));
        return http.build();
    }

    private OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");

        authorizationRequestResolver.setAuthorizationRequestCustomizer(builder -> {
            String registrationId = (String) builder.build().getAttributes()
                    .get(org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.REGISTRATION_ID);

            builder.additionalParameters(params -> {
                System.out.println(">>> OAuth2 로그인 시도 중! 서비스: " + registrationId);
                if ("naver".equalsIgnoreCase(registrationId)) {
                    params.remove("auth_type");
                } else {
                    params.put("prompt", "select_account");
                }
            });
        });
        return authorizationRequestResolver;
    }
}
