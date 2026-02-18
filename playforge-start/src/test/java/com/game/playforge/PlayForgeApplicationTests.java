package com.game.playforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:playforge;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "langchain4j.open-ai.chat-model.api-key=test-key",
        "langchain4j.open-ai.streaming-chat-model.api-key=test-key",
        "langchain4j.anthropic.chat-model.api-key=test-key",
        "langchain4j.anthropic.streaming-chat-model.api-key=test-key",
        "langchain4j.google-ai-gemini.chat-model.api-key=test-key",
        "langchain4j.google-ai-gemini.streaming-chat-model.api-key=test-key"
})
class PlayForgeApplicationTests {

    @Test
    void contextLoads() {
    }

}
