package com.codebox.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A missing static resource must be a 404, not a 500.
 *
 * It used to be swallowed by the catch-all handler, which logged a full stack trace at
 * ERROR for every /favicon.ico request. That is log noise, and log noise is how a real
 * 500 gets missed.
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("something genuinely broke");
        }

        @GetMapping("/missing")
        String missing() throws NoResourceFoundException {
            // mirrors what Spring's resource handler throws for an unknown path
            throw new NoResourceFoundException(
                    org.springframework.http.HttpMethod.GET, "/favicon.ico");
        }
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("a missing resource is 404 with a clean message, not 500")
    void missingResourceIsNotFound() throws Exception {
        mockMvc().perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @Test
    @DisplayName("a genuine failure still reports 500 and keeps its message generic")
    void genuineFailureIsStill500() throws Exception {
        mockMvc().perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                // internals must never leak to the client
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @Test
    void statusIsCarriedByApiException() {
        ApiException e = ApiException.notFound("代码片段不存在");
        org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        org.assertj.core.api.Assertions.assertThat(ApiException.conflict("x").getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        org.assertj.core.api.Assertions.assertThat(ApiException.unauthorized("x").getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
