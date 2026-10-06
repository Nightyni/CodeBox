package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolJson;
import com.codebox.agent.tool.ToolSchema;
import com.codebox.dto.SnippetRequest;
import com.codebox.entity.Snippet;
import com.codebox.service.SnippetService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates a new snippet.
 *
 * WRITE tool: the agent loop never runs this directly. It captures the arguments into a
 * pending action, and this executes only after the user approves - so the model cannot
 * silently add data.
 */
@Component
public class CreateSnippetTool implements AgentTool {

    private final SnippetService snippetService;
    private final ToolJson json;

    public CreateSnippetTool(SnippetService snippetService, ToolJson json) {
        this.snippetService = snippetService;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "create_snippet",
                "新建一条代码片段并保存到数据库。这是一个写操作，调用后需要用户确认才会真正写入。"
                        + "当用户要求保存、记录或创建一个新片段时使用。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("title", ToolSchema.string("片段标题"))
                                .put("content", ToolSchema.string("完整代码内容"))
                                .put("language", ToolSchema.string("编程语言，例如 Java、SQL、Python"))
                                .put("tags", ToolSchema.string("可选标签，英文逗号分隔"))
                                .build(),
                        List.of("title", "content", "language")));
    }

    @Override
    public RiskLevel risk() {
        return RiskLevel.WRITE;
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        Map<String, Object> args = json.parse(argumentsJson);
        if (args == null) return ToolResult.error("参数不是合法 JSON");

        String title = ToolJson.asString(args.get("title"));
        String content = ToolJson.asString(args.get("content"));
        String language = ToolJson.asString(args.get("language"));
        String tags = ToolJson.asString(args.get("tags"));

        if (title == null || title.isBlank()) return ToolResult.error("title 不能为空");
        if (content == null || content.isBlank()) return ToolResult.error("content 不能为空");
        if (language == null || language.isBlank()) return ToolResult.error("language 不能为空");
        if (title.length() > 100) return ToolResult.error("title 超过 100 字");
        if (content.length() > 20000) return ToolResult.error("content 超过 20000 字");

        SnippetRequest request = new SnippetRequest();
        request.setTitle(title.strip());
        request.setContent(content);
        request.setLanguage(language.strip());
        request.setTags(tags);

        Snippet created = snippetService.create(context.userId(), request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("id", created.getId());
        payload.put("title", created.getTitle());
        payload.put("tags", created.getTags());
        payload.put("summary", created.getSummary());
        payload.put("message", "已创建代码片段《" + created.getTitle() + "》，id=" + created.getId());
        return json.ok(payload);
    }
}
