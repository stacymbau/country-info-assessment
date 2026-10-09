package com.assessment.country_info_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CountryInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CountryInfoApplication.class, args);
    }
}
