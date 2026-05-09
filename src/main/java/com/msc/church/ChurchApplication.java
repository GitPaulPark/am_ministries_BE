package com.msc.church;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ChurchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChurchApplication.class, args);
    }
}
