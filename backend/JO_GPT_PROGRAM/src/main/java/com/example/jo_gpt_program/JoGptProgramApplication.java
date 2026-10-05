package com.example.jo_gpt_program;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.example.entitycom",
        "com.example.jo_gpt_program"
})
@EntityScan(basePackages = "com.example.entitycom.entity")
@ComponentScan(basePackages = {
        "com.example.entitycom.entity",
        "com.example.jo_gpt_program",
})
@EnableJpaRepositories(basePackages = {
        "com.example.jo_gpt_program"
})
@EnableJpaAuditing //  이것도 필요
    @EnableScheduling // 이게 반복작업 가능하게 해주는 어노테이션
public class JoGptProgramApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(JoGptProgramApplication.class)
                .initializers(new com.example.jo_gpt_program.gpt.config.ChromaInitializer()) // ← 이거 추가!

                .run(args);

    }

}
