package com.codebox.agent.tool.capability;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolJson;
import com.codebox.agent.tool.ToolSchema;
import com.codebox.entity.Snippet;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI capability: produce an improved version of a snippet.
 *
 * Deliberately READ-only: it returns the improved code plus a change list, and does
 * NOT write anything. Saving the result is a separate, user-confirmed step via
 * create_snippet, which keeps the original version intact and makes AI output
 * reviewable before it becomes data.
 */
@Component
public class OptimizeSnippetTool implements AgentTool {

    private final CodeAnalyzer codeAnalyzer;
    private final ToolJson json;

    public OptimizeSnippetTool(CodeAnalyzer codeAnalyzer, ToolJson json) {
        this.codeAnalyzer = codeAnalyzer;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "optimize_snippet",
                "针对指定代码片段生成一个优化版本，并列出改动说明。当用户要求优化、改进、重构代码时使用。"
                        + "该工具不会保存结果；如果用户想保留，需要再用 create_snippet 新建片段。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("id", ToolSchema.integer("要优化的片段 id"))
                                .put("focus", ToolSchema.string("可选，优化重点，例如 '性能'、'可读性'、'分页'"))
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
        String focus = ToolJson.asString(args.get("focus"));

        Snippet snippet = codeAnalyzer.load(context.userId(), id);
        if (snippet == null) {
            return ToolResult.error("找不到 id=" + id + " 的代码片段（可能不存在或不属于当前用户）");
        }

        String optimized = codeAnalyzer.optimize(snippet, focus);
        if (optimized == null || optimized.isBlank()) {
            return ToolResult.error("代码优化失败（模型不可用或调用出错）。"
                    + "请告知用户当前无法完成优化，不要编造优化结果。");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snippetId", id);
        payload.put("originalTitle", snippet.getTitle());
        payload.put("language", snippet.getLanguage());
        payload.put("optimizedCode", optimized);
        payload.put("note", "结果尚未保存。用户确认后，请调用 create_snippet 保存为新片段，"
                + "标题建议为「原标题 - AI优化版」，以保留原始版本。");
        return json.ok(payload);
    }
}
