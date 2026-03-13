package com.example.lightci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the LightCI Server.
 *
 * @EnableAsync enables Spring's async task execution, which is used by
 * JobService to run shell commands in a background thread so the HTTP
 * request returns immediately while the job runs.
 */
@SpringBootApplication
@EnableAsync
public class LightCiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LightCiApplication.class, args);
    }
}
