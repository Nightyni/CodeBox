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
 * AI capability: explain a snippet and point out problems.
 *
 * Registered as a tool (rather than left implicit in the prompt) so the run trace
 * shows an explicit {@code analyze_snippet} step, which is what makes the agent's
 * behaviour observable and explainable.
 */
@Component
public class AnalyzeSnippetTool implements AgentTool {

    private final CodeAnalyzer codeAnalyzer;
    private final ToolJson json;

    public AnalyzeSnippetTool(CodeAnalyzer codeAnalyzer, ToolJson json) {
        this.codeAnalyzer = codeAnalyzer;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "analyze_snippet",
                "分析指定代码片段：说明它做什么、核心逻辑、潜在问题和优化建议。当用户要求解释代码、"
                        + "评审代码、或问“这段代码有没有问题”时使用。只读，不会修改数据。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("id", ToolSchema.integer("要分析的片段 id"))
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

        Snippet snippet = codeAnalyzer.load(context.userId(), id);
        if (snippet == null) {
            return ToolResult.error("找不到 id=" + id + " 的代码片段（可能不存在或不属于当前用户）");
        }

        String analysis = codeAnalyzer.analyze(snippet);
        if (analysis == null || analysis.isBlank()) {
            return ToolResult.error("代码分析失败（模型不可用或调用出错）。"
                    + "请告知用户当前无法完成分析，不要编造分析结果。");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snippetId", id);
        payload.put("title", snippet.getTitle());
        payload.put("analysis", analysis);
        return json.ok(payload);
    }
}
