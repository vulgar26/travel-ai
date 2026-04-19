package com.travel.ai;

import com.travel.ai.config.AppAgentProperties;
import com.travel.ai.config.AppConversationProperties;
import com.travel.ai.config.AppEvalProperties;
import com.travel.ai.config.AppMemoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppAgentProperties.class, AppEvalProperties.class, AppConversationProperties.class, AppMemoryProperties.class})
public class TravelAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelAiApplication.class, args);
    }
}