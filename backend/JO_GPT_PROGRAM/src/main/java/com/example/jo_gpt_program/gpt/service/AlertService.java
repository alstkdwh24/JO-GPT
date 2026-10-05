package com.example.jo_gpt_program.gpt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service("alertService")
public class AlertService {

    // ConcurrentHashMap 은 자바에서 여러 개의 쓰레드가 동시에 접근하더라도 데이터의 안정성을 보장하면서 성능까지 챙긴 해시맵이다.
    // HashMap은 자바에서 데이터를 '키(Key)'와 '값(Value)'의 쌍으로 묶어서 저장하는 아주 대표적인 바구니(자료구조)입니다.
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 프론트에서 SSE 연결할때
    public SseEmitter getEmitter(Long memberKey) {
        // ✅ new SseEmitter()로 새로 생성 (30분 타임아웃)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.put(memberKey, emitter);

        // 연결 끝기면 자동 제거
        // 키가 같으면 제거
        // memberKey만 같아도 제거가 가능하지만 정확성을 높이기 위해 emitter 도 넣어주는 것
        emitter.onCompletion(() -> emitters.remove(memberKey, emitter));
        emitter.onTimeout(() -> emitters.remove(memberKey, emitter));
        return emitter;
    }

    // 2. 알림 전송
    public void sendAlert(Long memberKey, String message) throws IOException {
        log.info("SSE 연결 생성 memberKey={}");

        SseEmitter emitter = emitters.get(memberKey);
        if (emitter != null) {
            try {
                emitter.send(message);
            } catch (Exception e) {
                emitters.remove(memberKey, emitter);
            }
        }
    }

    // 연결 해제
    public void disconnect(Long memberKey) {
        SseEmitter emitter = emitters.get(memberKey);
        if (emitter != null) {
            emitters.remove(memberKey, emitter);
        }
    }
}
