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
 * Modifies an existing snippet in place.
 *
 * WRITE tool, and additionally destructive: it overwrites the original content, which
 * is why it goes through the same approval gate as deletion.
 */
@Component
public class UpdateSnippetTool implements AgentTool {

    private final SnippetService snippetService;
    private final ToolJson json;

    public UpdateSnippetTool(SnippetService snippetService, ToolJson json) {
        this.snippetService = snippetService;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "update_snippet",
                "修改已存在的代码片段（会覆盖原内容）。这是一个写操作，调用后需要用户确认才会真正写入。"
                        + "如果要保留原版本，请改用 create_snippet 新建。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("id", ToolSchema.integer("要修改的片段 id"))
                                .put("title", ToolSchema.string("新标题；不修改则省略"))
                                .put("content", ToolSchema.string("新代码内容；不修改则省略"))
                                .put("language", ToolSchema.string("新语言；不修改则省略"))
                                .put("tags", ToolSchema.string("新标签；不修改则省略"))
                                .build(),
                        List.of("id")));
    }

    @Override
    public RiskLevel risk() {
        return RiskLevel.WRITE;
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        Map<String, Object> args = json.parse(argumentsJson);
        if (args == null) return ToolResult.error("参数不是合法 JSON");

        Long id = ToolJson.asLong(args.get("id"));
        if (id == null) return ToolResult.error("id 不能为空且必须是数字");

        // Load current values first so omitted fields keep their existing value.
        Snippet existing = snippetService.findById(id, context.userId());
        if (existing == null) {
            return ToolResult.error("找不到 id=" + id + " 的代码片段（可能不存在或不属于当前用户）");
        }

        String title = orDefault(ToolJson.asString(args.get("title")), existing.getTitle());
        String content = orDefault(ToolJson.asString(args.get("content")), existing.getContent());
        String language = orDefault(ToolJson.asString(args.get("language")), existing.getLanguage());
        String tags = ToolJson.asString(args.get("tags"));
        if (tags == null) tags = existing.getTags();

        if (title.isBlank()) return ToolResult.error("title 不能为空");
        if (content.isBlank()) return ToolResult.error("content 不能为空");
        if (language.isBlank()) return ToolResult.error("language 不能为空");

        SnippetRequest request = new SnippetRequest();
        request.setTitle(title.strip());
        request.setContent(content);
        request.setLanguage(language.strip());
        request.setTags(tags);

        Snippet updated = snippetService.update(context.userId(), id, request);
        if (updated == null) {
            return ToolResult.error("修改失败：片段不存在或不属于当前用户");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("id", updated.getId());
        payload.put("title", updated.getTitle());
        payload.put("message", "已更新代码片段《" + updated.getTitle() + "》");
        return json.ok(payload);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
