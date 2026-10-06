package com.codebox.entity;

import java.time.LocalDateTime;

/** Persisted vector for a snippet, so retrieval survives restarts. */
public class SnippetEmbedding {

    private Long snippetId;
    private Long userId;
    private String model;
    /** Comma-separated float vector. */
    private String vector;
    private Integer dimensions;
    private LocalDateTime updateTime;

    public Long getSnippetId() { return snippetId; }
    public void setSnippetId(Long snippetId) { this.snippetId = snippetId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getVector() { return vector; }
    public void setVector(String vector) { this.vector = vector; }

    public Integer getDimensions() { return dimensions; }
    public void setDimensions(Integer dimensions) { this.dimensions = dimensions; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
