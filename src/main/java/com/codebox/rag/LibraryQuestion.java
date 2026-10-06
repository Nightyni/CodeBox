package com.codebox.rag;

import java.util.regex.Pattern;

/**
 * Detects questions that ask about the library itself rather than about its contents,
 * e.g. "我的知识库有哪些内容".
 *
 * These must NOT go through vector retrieval: the phrasing has almost no lexical
 * overlap with any snippet, so the nearest-neighbour result is arbitrary noise
 * (measured ~0.06 cosine) yet gets presented to the user as a citation.
 *
 * Being conservative matters more than being clever: a false positive hijacks a real
 * question ("我的知识库里有没有分页的实现？" must still search), while a false negative
 * merely falls back to normal retrieval.
 */
public final class LibraryQuestion {

    private LibraryQuestion() {}

    /**
     * Words that refer to the library itself.
     *
     * "代码" / "程序" are included because users phrase this as "我的代码有哪些",
     * not "我的知识库有哪些内容". The first version only recognised the latter, so
     * the former fell through to vector search and returned an arbitrary neighbour
     * (measured ~0.06 cosine) which was then shown as if it were a real citation.
     */
    private static final String LIBRARY = "知识库|代码库|片段库|代码|程序|仓库|收藏";

    /** "…有哪些内容" / "…有多少条" / "…有什么" / "…统计" */
    private static final Pattern LIBRARY_OVERVIEW = Pattern.compile(
            "(?:" + LIBRARY + ")[^。？?]{0,8}(?:内容|有什么|有哪些|哪些|多少|几条|统计|列表)");

    /**
     * Bare form with no library word: "有哪些内容" / "有没有代码" / "有多少条".
     *
     * Deliberately narrow: a broad "哪些" rule would hijack real questions such as
     * "有哪些分页的代码", which must still go to retrieval.
     */
    private static final Pattern BARE_OVERVIEW = Pattern.compile(
            "^(?:都|一共|总共)?(?:有)?(?:哪些|什么)(?:内容|东西|片段|代码)?$|"
                    + "^(?:都|一共|总共)?有没有(?:内容|东西|片段|代码)?$|"
                    + "^(?:一共|总共)?有多少(?:条|个|段)$");

    public static boolean isOverview(String question) {
        if (question == null || question.isBlank()) return false;
        String q = question.replaceAll("\\s+", "");
        if (q.length() > 30) return false;

        return LIBRARY_OVERVIEW.matcher(q).find() || BARE_OVERVIEW.matcher(q).find();
    }
}
