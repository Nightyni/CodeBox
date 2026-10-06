package com.codebox.agent.tool.capability;

import com.codebox.entity.Snippet;
import com.codebox.llm.ChatModel;
import com.codebox.rag.AnswerGenerator;
import com.codebox.service.SnippetService;
import org.springframework.stereotype.Service;

/**
 * LLM-backed code understanding, shared by the capability tools.
 *
 * Kept separate from the tools so the prompts live in one place and can be unit-tested
 * against a fake {@link ChatModel} without touching the agent loop.
 */
@Service
public class CodeAnalyzer {

    private static final String ANALYZE_SYSTEM = """
            你是一名资深工程师，正在评审一段代码。请用中文、按下面的固定结构输出，不要额外寒暄：

            功能：一句话说明这段代码做什么。
            核心逻辑：
            1. ...
            2. ...
            潜在问题：
            - ...（没有则写“未发现明显问题”）
            优化建议：
            - ...（没有则写“无”）

            要求：结论必须基于代码本身，不要臆测未出现的技术栈；如果信息不足以判断，明确说“不足以判断”。
            """;

    private static final String OPTIMIZE_SYSTEM = """
            你是一名资深工程师。请针对给定的代码给出一个改进版本。

            输出必须严格按下面的格式，不要多余解释：
            ### 改动说明
            - 逐条说明改了什么、为什么
            ### 优化后的代码
            ```
            （完整的、可直接使用的代码，不要省略）
            ```

            要求：
            1. 保持原有的语言与用途，不要改变业务语义。
            2. 只做必要的改进，不要为了炫技重写。
            3. 如果代码本身已经足够好，就说明“无需优化”，并给出当前代码即可。
            4. 用中文写改动说明。
            """;

    private final ChatModel chatModel;
    private final SnippetService snippetService;

    public CodeAnalyzer(ChatModel chatModel, SnippetService snippetService) {
        this.chatModel = chatModel;
        this.snippetService = snippetService;
    }

    /** @return the snippet when owned by the user, otherwise null. */
    public Snippet load(Long userId, Long snippetId) {
        return snippetService.findById(snippetId, userId);
    }

    public String analyze(Snippet snippet) {
        return chatModel.complete(ANALYZE_SYSTEM, describe(snippet));
    }

    public String optimize(Snippet snippet, String focus) {
        String prompt = describe(snippet)
                + (focus == null || focus.isBlank() ? "" : "\n\n优化重点：" + focus);
        return chatModel.complete(OPTIMIZE_SYSTEM, prompt);
    }

    private String describe(Snippet snippet) {
        return """
                标题: %s
                语言: %s
                标签: %s

                代码:
                %s
                """.formatted(
                nullSafe(snippet.getTitle()),
                nullSafe(snippet.getLanguage()),
                nullSafe(snippet.getTags()),
                AnswerGenerator.truncate(nullSafe(snippet.getContent()), 6000));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
