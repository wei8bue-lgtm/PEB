package com.peb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application class for Production Engineering Backbone (PEB)
 * A web-based CAD and eBOM management system with revision control and workflow support
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
public class PEBApplication {

    public static void main(String[] args) {
        SpringApplication.run(PEBApplication.class, args);
    }
}