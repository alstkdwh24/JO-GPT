package com.example.jogptreal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing // 이 설정이 있어야 @CreatedDate가 동작합니다.
public class AiUniversalControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiUniversalControllerApplication.class, args);
    }

}
