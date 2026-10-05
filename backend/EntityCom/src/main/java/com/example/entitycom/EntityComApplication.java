package com.example.entitycom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EntityComApplication {

    public static void main(String[] args) {
        System.setProperty("spring.config.name", ""); // application.properties 로드 안 함
        SpringApplication.run(EntityComApplication.class, args);
    }

}
