package com.zwskchat.zwskchat.ai.hub;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresalesIntentClassifierTest {

    @Test
    void classifiesSolutionDrafting() {
        assertThat(PresalesIntentClassifier.classify("帮我草拟一版科研数据治理的技术方案"))
                .isEqualTo(PresalesTaskDomain.SOLUTION_DRAFTING);
        assertThat(PresalesIntentClassifier.classify("ERP 和 RAG 怎么结合落地"))
                .isEqualTo(PresalesTaskDomain.SOLUTION_DRAFTING);
        assertThat(PresalesIntentClassifier.classify("如何提升 RAG 回答深度"))
                .isEqualTo(PresalesTaskDomain.SOLUTION_DRAFTING);
    }

    @Test
    void classifiesNeedMatching() {
        assertThat(PresalesIntentClassifier.classify("我们是省级科研院所，想要一套知识检索和售前演示系统"))
                .isEqualTo(PresalesTaskDomain.NEED_MATCHING);
        assertThat(PresalesIntentClassifier.classify("你们能做什么"))
                .isEqualTo(PresalesTaskDomain.NEED_MATCHING);
    }

    @Test
    void classifiesCaseRetrieval() {
        assertThat(PresalesIntentClassifier.classify("有没有做过先进材料行业的成功案例"))
                .isEqualTo(PresalesTaskDomain.CASE_RETRIEVAL);
        assertThat(PresalesIntentClassifier.classify("给我几个类似项目参考"))
                .isEqualTo(PresalesTaskDomain.CASE_RETRIEVAL);
    }

    @Test
    void classifiesMaterialQuery() {
        assertThat(PresalesIntentClassifier.classify("贵公司的资质和简介是什么"))
                .isEqualTo(PresalesTaskDomain.MATERIAL_QUERY);
        assertThat(PresalesIntentClassifier.classify("这个产品的参数和报价"))
                .isEqualTo(PresalesTaskDomain.MATERIAL_QUERY);
    }

    @Test
    void fallsBackToGeneral() {
        assertThat(PresalesIntentClassifier.classify("你好")).isEqualTo(PresalesTaskDomain.GENERAL);
        assertThat(PresalesIntentClassifier.classify("今天天气不错")).isEqualTo(PresalesTaskDomain.GENERAL);
        assertThat(PresalesIntentClassifier.classify(null)).isEqualTo(PresalesTaskDomain.GENERAL);
        assertThat(PresalesIntentClassifier.classify("   ")).isEqualTo(PresalesTaskDomain.GENERAL);
    }

    @Test
    void solutionDraftingHasHighestPriorityOverOtherSignals() {
        // 同时含“案例”（案例检索信号）与“草拟方案”（方案草拟信号）时，方案草拟优先
        assertThat(PresalesIntentClassifier.classify("参考做过的案例，帮我草拟一版方案"))
                .isEqualTo(PresalesTaskDomain.SOLUTION_DRAFTING);
    }
}
