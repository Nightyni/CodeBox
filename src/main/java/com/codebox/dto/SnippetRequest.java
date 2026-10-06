package com.codebox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SnippetRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;

    @NotBlank(message = "代码内容不能为空")
    @Size(max = 20000, message = "代码内容长度不能超过 20000")
    private String content;

    @NotBlank(message = "请选择编程语言")
    @Size(max = 30, message = "语言长度不能超过 30")
    private String language;

    @Size(max = 200, message = "标签长度不能超过 200")
    private String tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
}
