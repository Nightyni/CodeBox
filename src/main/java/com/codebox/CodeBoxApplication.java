package com.codebox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CodeBox — AI 代码知识检索与问答助手。
 *
 * 把团队沉淀的代码片段做成"可语义检索、可问答"的知识库，
 * 解决重复造轮子 / 新人上手慢的问题。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CodeBoxApplication {

    /** Gitignored location for local secrets such as the DeepSeek API key. */
    private static final Path LOCAL_CONFIG = Path.of("config", "application-local.yml");

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CodeBoxApplication.class);
        // Load the local config only when it exists, so a fresh clone still starts.
        if (Files.isRegularFile(LOCAL_CONFIG)) {
            app.setAdditionalProfiles();
            System.setProperty("spring.config.additional-location",
                    "optional:file:./" + LOCAL_CONFIG.toString().replace('\\', '/'));
        }
        app.run(args);
    }
}