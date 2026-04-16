package com.artha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ArthaApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArthaApplication.class, args);
    }
}
