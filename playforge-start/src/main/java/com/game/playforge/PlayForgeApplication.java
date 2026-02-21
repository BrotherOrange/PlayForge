package com.game.playforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlayForgeApplication {

    static {
        // LangChain4j 1.11 may detect multiple HTTP client factories on classpath.
        // Pin to Spring RestClient factory unless explicitly overridden.
        String key = "langchain4j.http.clientBuilderFactory";
        if (System.getProperty(key) == null || System.getProperty(key).isBlank()) {
            System.setProperty(
                    key,
                    "dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilderFactory"
            );
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(PlayForgeApplication.class, args);
    }

}
