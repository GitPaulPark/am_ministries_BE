package com.msc.church;

import com.msc.church.meeting.ai.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(AiProperties.class)
public class ChurchApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChurchApplication.class, args);
    }
}
