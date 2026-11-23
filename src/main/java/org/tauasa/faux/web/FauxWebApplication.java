package org.tauasa.faux.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Starts the web app. */
@SpringBootApplication
@EnableConfigurationProperties(FauxWebProperties.class)
public class FauxWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(FauxWebApplication.class, args);
    }
}
