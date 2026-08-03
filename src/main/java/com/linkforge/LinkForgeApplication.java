package com.linkforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LinkForge — Secure, scalable, analytics-driven URL management platform.
 *
 * Tech Stack: Java 21 (Virtual Threads) + Spring Boot 3.3.x + PostgreSQL + Redis
 * Architecture: Modular monolith, ready for microservices extraction.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class LinkForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinkForgeApplication.class, args);
    }
}
