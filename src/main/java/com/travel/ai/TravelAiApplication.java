package com.travel.ai;

import com.travel.ai.config.AppAgentProperties;
import com.travel.ai.config.AppConversationProperties;
import com.travel.ai.config.AppEvalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppAgentProperties.class, AppEvalProperties.class, AppConversationProperties.class})
public class TravelAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelAiApplication.class, args);
    }
}