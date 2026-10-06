package com.codebox.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryQuestionTest {

    @Test
    @DisplayName("overview questions are detected (the screenshot cases)")
    void detectsOverviewQuestions() {
        assertThat(LibraryQuestion.isOverview("我的知识库有哪些内容")).isTrue();
        assertThat(LibraryQuestion.isOverview("知识库里有什么")).isTrue();
        assertThat(LibraryQuestion.isOverview("我的代码库有多少条")).isTrue();
        assertThat(LibraryQuestion.isOverview("片段库 统计")).isTrue();
        assertThat(LibraryQuestion.isOverview("有哪些内容")).isTrue();

        // Regression: the user asked this exact phrasing and it fell through to vector
        // search, which returned an unrelated snippet as a "citation".
        assertThat(LibraryQuestion.isOverview("我的代码有哪些")).isTrue();
        assertThat(LibraryQuestion.isOverview("我的程序有哪些")).isTrue();
        assertThat(LibraryQuestion.isOverview("有哪些代码")).isTrue();
        assertThat(LibraryQuestion.isOverview("有没有代码")).isTrue();
    }

    @Test
    @DisplayName("real content questions are NOT treated as overview questions")
    void doesNotHijackContentQuestions() {
        assertThat(LibraryQuestion.isOverview("分页怎么做")).isFalse();
        assertThat(LibraryQuestion.isOverview("redis 分布式锁怎么实现")).isFalse();
        assertThat(LibraryQuestion.isOverview("怎么用 Stream 过滤集合")).isFalse();
        assertThat(LibraryQuestion.isOverview("")).isFalse();
        assertThat(LibraryQuestion.isOverview(null)).isFalse();

        // Must still be searched: these are real questions that merely contain 代码/哪些
        assertThat(LibraryQuestion.isOverview("有哪些分页的代码")).isFalse();
        assertThat(LibraryQuestion.isOverview("分页的代码怎么写")).isFalse();
        assertThat(LibraryQuestion.isOverview("代码里怎么做分页")).isFalse();
    }

    @Test
    @DisplayName("a long, specific question is treated as a real question")
    void longQuestionIsNotOverview() {
        assertThat(LibraryQuestion.isOverview(
                "帮我找一下用 MyBatis 做分页并且要返回总数的那段代码是怎么实现的"))
                .isFalse();
    }
}