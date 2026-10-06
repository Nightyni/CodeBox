package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolJson;
import com.codebox.agent.tool.ToolSchema;
import com.codebox.entity.Snippet;
import com.codebox.service.SnippetService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one snippet in full, including its complete code.
 *
 * Separate from search because a search result is truncated: the model must fetch the
 * whole body before it can meaningfully analyse or optimise it.
 */
@Component
public class GetSnippetTool implements AgentTool {

    private static final int MAX_CONTENT_CHARS = 8000;

    private final SnippetService snippetService;
    private final ToolJson json;

    public GetSnippetTool(SnippetService snippetService, ToolJson json) {
        this.snippetService = snippetService;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "get_snippet",
                "按 id 获取某条代码片段的完整内容（含完整代码）。在分析、优化或修改某段代码之前必须先调用它。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("id", ToolSchema.integer("代码片段 id"))
                                .build(),
                        List.of("id")));
    }

    @Override
    public RiskLevel risk() {
        return RiskLevel.READ;
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        Map<String, Object> args = json.parse(argumentsJson);
        if (args == null) return ToolResult.error("参数不是合法 JSON");

        Long id = ToolJson.asLong(args.get("id"));
        if (id == null) return ToolResult.error("id 不能为空且必须是数字");

        // Ownership is enforced in SQL, so this cannot read another user's snippet.
        Snippet snippet = snippetService.findById(id, context.userId());
        if (snippet == null) {
            return ToolResult.error("找不到 id=" + id + " 的代码片段（可能不存在或不属于当前用户）");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", snippet.getId());
        payload.put("title", snippet.getTitle());
        payload.put("language", snippet.getLanguage());
        payload.put("tags", snippet.getTags());
        payload.put("summary", snippet.getSummary());
        payload.put("content", truncate(snippet.getContent()));
        return json.ok(payload);
    }

    private static String truncate(String content) {
        if (content == null) return "";
        return content.length() <= MAX_CONTENT_CHARS
                ? content
                : content.substring(0, MAX_CONTENT_CHARS) + "\n... (内容过长已截断)";
    }
}
