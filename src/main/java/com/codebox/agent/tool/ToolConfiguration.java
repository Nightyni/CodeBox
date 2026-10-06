package com.codebox.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the tool-layer helpers that need Jackson. */
@Configuration
public class ToolConfiguration {

    @Bean
    public ToolJson toolJson(ObjectMapper objectMapper) {
        return new ToolJson(objectMapper);
    }
}
