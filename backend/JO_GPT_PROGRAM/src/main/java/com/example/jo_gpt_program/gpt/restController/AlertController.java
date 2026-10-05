package com.example.jo_gpt_program.gpt.restController;

import com.example.jo_gpt_program.gpt.config.filter.UserInfoDto;
import com.example.jo_gpt_program.gpt.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequestMapping("/alert")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }
    // SecurityContext에서 JwtDelegateFilter가 저장한 UserInfoDto를 꺼내서 SSE 연결

    @GetMapping("/connects")
    public SseEmitter connect() {
        log.info("12345678910");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.warn("SSE 연결 요청 - 인증 정보 없음!");
            return null;
        }
        // ✅ jo_gpt_program 패키지의 UserInfoDto 사용 (JwtDelegateFilter와 동일!)
        UserInfoDto userInfo = (UserInfoDto) auth.getPrincipal();
        log.info("SSE 연결 요청 memberKey={}", userInfo.getId());
        return alertService.getEmitter(Long.valueOf(userInfo.getMemberId()));
    }
}
