package com.englishlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aplicación Spring Boot. Excluye la autoconfiguración de UserDetailsService porque la
 * autenticación es JWT propia y no debe generarse un usuario en memoria.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
@EnableAsync
public class EnglishLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnglishLabApplication.class, args);
    }
}
