package com.ttn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportTicketAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportTicketAiApplication.class, args);
    }
}