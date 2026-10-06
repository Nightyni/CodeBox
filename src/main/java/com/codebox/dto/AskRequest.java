package com.codebox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for /api/ask and /api/ask/retrieve.
 *
 * Previously the controller took a raw {@code Map<String, String>}, which bypassed Bean
 * Validation entirely: a blank or over-long question reached the service layer and the
 * failure surfaced as a 500 instead of a field-level 400. Every other endpoint already
 * used a validated DTO, so this was the odd one out.
 */
public class AskRequest {

    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题过长")
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
