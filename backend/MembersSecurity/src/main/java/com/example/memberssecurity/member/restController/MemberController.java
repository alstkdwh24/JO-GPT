package com.example.memberssecurity.member.restController;

import com.example.entitycom.entity.member.Members;
import com.example.memberssecurity.member.dto.request.LoginDto;
import com.example.memberssecurity.member.dto.request.SignUpDto;
import com.example.memberssecurity.member.dto.response.MemberDto;
import com.example.memberssecurity.member.service.LoginAttemptService;
import com.example.memberssecurity.member.service.MemberService;
import com.example.memberssecurity.member.service.RefreshTokenService;
import com.example.memberssecurity.security.config.dto.CustomUserDetails;
import com.example.memberssecurity.security.config.jwt.JWTUtils;
import com.example.memberssecurity.security.config.jwt.TokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/login")
@Slf4j
public class MemberController {
private final String memberSecurityUrl;
    private final MemberService memberService;
    private final JWTUtils jWTUtils;
    private final RefreshTokenService refreshTokenService;
    private final TokenCookies tokenCookies;
    private final LoginAttemptService loginAttemptService;

    public MemberController(@Value("${MEMBER_SECURITY_URL}") String memberSecurityUrl, MemberService memberService, JWTUtils jWTUtils, RefreshTokenService refreshTokenService, TokenCookies tokenCookies, LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
        this.memberSecurityUrl = memberSecurityUrl;
        this.memberService = memberService;
        this.jWTUtils = jWTUtils;
        this.refreshTokenService = refreshTokenService;
        this.tokenCookies = tokenCookies;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<MemberDto> login(@RequestBody  LoginDto dto, HttpServletResponse response){

        // 무차별 대입 방지: 15분 안에 5번 실패한 아이디는 차단 (429)
        if (loginAttemptService.isBlocked(dto.getMemberId())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(LoginAttemptService.WINDOW.toSeconds())).build();
        }
        Members member;
        try {
            member = memberService.login(dto);
        } catch (RuntimeException e) {
            loginAttemptService.onFailure(dto.getMemberId());
            throw e;
        }
        loginAttemptService.onSuccess(dto.getMemberId());
        // access 30분 + refresh 14일(Redis 저장·회전)
        tokenCookies.write(response, refreshTokenService.issue(member.getMemberKey(), member.getRole()));

        return ResponseEntity.ok(MemberDto.builder()
                .memberId(member.getMemberId())
                .nickname(member.getNickname())
                .role(member.getRole())
                .build());    }

    @PostMapping("/signUp")
    public ResponseEntity<String> signUp(@RequestBody SignUpDto dto) {
        try {
            memberService.signUp(dto);
            return ResponseEntity.ok("success");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @GetMapping("/myInfo")
    public ResponseEntity<?> getMemberInfo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 1. 인증 정보 및 익명 사용자 체크
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }
        // 사용자 정보를 가져오기 위한 객체 생성
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        // 사용자 ID 추출
        Long memberId = userDetails.getMemberId();

        return memberService.memberUserInfo(memberId)
                .<ResponseEntity<?>>map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/nickname")
    public ResponseEntity<String> updateNickname(@RequestBody Map<String, String> body) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }
        String nickname = body.get("nickname");
        if (nickname == null || nickname.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("닉네임을 입력해주세요.");
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        memberService.updateNickname(userDetails.getMemberId(), nickname.trim());
        return ResponseEntity.ok("success");
    }

    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // access jti 블랙리스트 + Redis refresh 삭제
        refreshTokenService.revoke(TokenCookies.resolveAccessToken(request), TokenCookies.read(request, TokenCookies.REFRESH));
        tokenCookies.clear(response);

        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        // @RestController에서는 "redirect:..." 문자열을 반환하면 리다이렉트되지 않음
        // 직접 response를 사용하여 리다이렉트 시킴
        response.sendRedirect(memberSecurityUrl +"/home/GPT-Home");
    }

}
