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
 * Deletes a snippet.
 *
 * The most destructive tool, so the approval gate matters most here: the model can
 * propose a deletion but never perform one on its own.
 */
@Component
public class DeleteSnippetTool implements AgentTool {

    private final SnippetService snippetService;
    private final ToolJson json;

    public DeleteSnippetTool(SnippetService snippetService, ToolJson json) {
        this.snippetService = snippetService;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "delete_snippet",
                "删除一条代码片段。这是一个不可撤销的写操作，调用后需要用户确认才会真正删除。"
                        + "删除前建议先用 get_snippet 确认目标，并向用户说明将要删除的是哪一条。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("id", ToolSchema.integer("要删除的片段 id"))
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

        Snippet existing = snippetService.findById(id, context.userId());
        if (existing == null) {
            return ToolResult.error("找不到 id=" + id + " 的代码片段（可能不存在或不属于当前用户）");
        }

        boolean removed = snippetService.delete(context.userId(), id);
        if (!removed) {
            return ToolResult.error("删除失败：片段不存在或不属于当前用户");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("id", id);
        payload.put("title", existing.getTitle());
        payload.put("message", "已删除代码片段《" + existing.getTitle() + "》");
        return json.ok(payload);
    }
}
