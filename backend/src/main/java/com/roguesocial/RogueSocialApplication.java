package com.roguesocial;

import java.util.Random;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RogueSocialApplication {

    public static void main(String[] args) {
        SpringApplication.run(RogueSocialApplication.class, args);
    }

    @Bean
    Random random() {
        return new Random();
    }
}
