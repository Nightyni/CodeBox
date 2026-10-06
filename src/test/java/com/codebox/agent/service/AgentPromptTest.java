package com.codebox.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wording of the agent's system prompt.
 *
 * The prompt is behaviour, not documentation: when it told the model "write operations
 * need user confirmation" without saying who asks, the model decided to ask in prose
 * ("这会覆盖原片段，请确认是否执行") and stopped - so the backend never created a
 * pending action and the user never saw a confirmation button. The whole write flow was
 * dead, while every test still passed, because the loop and the approval state machine
 * were both correct.
 *
 * These assertions are coarse on purpose: they lock the presence of the instruction, not
 * its exact phrasing.
 */
class AgentPromptTest {

    private final String prompt = AgentPrompt.SYSTEM;

    @Test
    @DisplayName("the prompt tells the model to CALL the write tool, not to ask in text")
    void promptDelegatesConfirmationToTheSystem() {
        assertThat(prompt)
                .as("must state that the system, not the model, asks for confirmation")
                .contains("系统的职责")
                .contains("确认按钮");

        assertThat(prompt)
                .as("must explicitly forbid asking the user in prose before calling the tool")
                .contains("反例")
                .contains("正例");
    }

    @Test
    @DisplayName("the failed wording is not present")
    void promptDoesNotRevertToTheBrokenWording() {
        // The old text said only this, which the model read as "ask first, then call".
        assertThat(prompt).doesNotContain("调用它们时，请先用一句话向用户说明你打算做什么");
    }

    @Test
    @DisplayName("the prompt still forbids claiming a write already happened")
    void promptForbidsClaimingSuccess() {
        assertThat(prompt)
                .contains("不要声称")
                .contains("已保存");
    }

    @Test
    @DisplayName("covering vs new-snippet intent is spelled out with tool arguments")
    void promptMapsIntentsToTools() {
        assertThat(prompt).contains("update_snippet");
        assertThat(prompt).contains("create_snippet");
        assertThat(prompt).contains("optimize_snippet");
        assertThat(prompt).contains("AI优化版");
    }

    @Test
    @DisplayName("the prompt keeps the grounding rules")
    void promptKeepsGroundingRules() {
        assertThat(prompt)
                .as("must not let the model invent data")
                .contains("不要编造");
        assertThat(prompt)
                .as("must require real data before answering about the user's code")
                .contains("search_snippets");
    }

    @Test
    void outageNoteExists() {
        assertThat(AgentPrompt.TOOL_OUTAGE_NOTE).isNotBlank();
    }
}
