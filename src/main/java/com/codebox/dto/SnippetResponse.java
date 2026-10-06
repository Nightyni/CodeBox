package com.codebox.dto;

import com.codebox.entity.Snippet;
import java.time.LocalDateTime;

public record SnippetResponse(
        Long id,
        String title,
        String content,
        String language,
        String tags,
        String summary,
        Integer useCount,
        LocalDateTime createTime
) {
    public static SnippetResponse from(Snippet s) {
        return new SnippetResponse(
                s.getId(), s.getTitle(), s.getContent(), s.getLanguage(),
                s.getTags(), s.getSummary(), s.getUseCount(), s.getCreateTime());
    }
}
