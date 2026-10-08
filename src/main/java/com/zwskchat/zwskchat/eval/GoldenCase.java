package com.zwskchat.zwskchat.eval;

import java.util.List;

/**
 * 金集用例（对应工程计划书“测试评估体系”中的检索金集 / 答案金集 / 风险金集）。
 *
 * @param id              用例 ID
 * @param type            金集类型
 * @param question        用户问题
 * @param expectedDocIds  期望命中的文档 ID（检索金集用）
 * @param expectedKeywords 答案应包含的关键词（答案金集用）
 * @param shouldRefuse    是否应被拒答 / 拦截（风险金集用：true 表示该问题必须被拒答）
 */
public record GoldenCase(String id,
                         GoldenType type,
                         String question,
                         List<Long> expectedDocIds,
                         List<String> expectedKeywords,
                         boolean shouldRefuse) {

    public GoldenCase {
        expectedDocIds = expectedDocIds == null ? List.of() : List.copyOf(expectedDocIds);
        expectedKeywords = expectedKeywords == null ? List.of() : List.copyOf(expectedKeywords);
    }

    public static GoldenCase retrieval(String id, String question, List<Long> expectedDocIds) {
        return new GoldenCase(id, GoldenType.RETRIEVAL, question, expectedDocIds, List.of(), false);
    }

    public static GoldenCase answer(String id, String question, List<String> expectedKeywords) {
        return new GoldenCase(id, GoldenType.ANSWER, question, List.of(), expectedKeywords, false);
    }

    public static GoldenCase risk(String id, String question) {
        return new GoldenCase(id, GoldenType.RISK, question, List.of(), List.of(), true);
    }

    public enum GoldenType {
        /** 检索金集：标注应命中的文档/片段。 */
        RETRIEVAL,
        /** 答案金集：标注答案应覆盖的关键词。 */
        ANSWER,
        /** 风险金集：越权 / 泄漏 / 越界承诺 / 注入，必须被拒答。 */
        RISK
    }
}
