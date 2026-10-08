package com.zwskchat.zwskchat.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多路召回融合（Reciprocal Rank Fusion, RRF）。
 *
 * <p>对应工程计划书“多路召回：dense + BM25/keyword + metadata filter”要求：
 * 把若干条独立排好序的候选列表（如向量召回、关键词召回）融合成一个统一排序，
 * 避免不同召回通道分数量纲不一致的问题。RRF 公式：
 * <pre>score(d) = Σ_lists 1 / (k + rank_list(d))</pre>
 * 其中 rank 从 1 开始；k 为平滑常数（典型 60）。该实现为纯函数，便于单元测试。
 */
@Component
public class HybridFusion {

    /** RRF 平滑常数的常用默认值。 */
    public static final int DEFAULT_K = 60;

    /**
     * 融合多路排好序的 ID 列表，返回按融合分降序的 ID 列表。
     *
     * @param rankedLists 多路召回结果，每个列表内部已按相关度从高到低排序
     * @param k           RRF 平滑常数（&gt; 0）
     * @param limit       返回上限（&le; 0 表示不限制）
     */
    public List<Long> fuse(List<List<Long>> rankedLists, int k, int limit) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        if (rankedLists != null) {
            int safeK = Math.max(1, k);
            for (List<Long> list : rankedLists) {
                if (list == null) continue;
                for (int rank = 0; rank < list.size(); rank++) {
                    Long id = list.get(rank);
                    if (id == null) continue;
                    double contribution = 1.0D / (safeK + rank + 1);
                    scores.merge(id, contribution, Double::sum);
                }
            }
        }

        List<Map.Entry<Long, Double>> entries = new ArrayList<>(scores.entrySet());
        entries.sort((a, b) -> {
            int byScore = Double.compare(b.getValue(), a.getValue());
            if (byScore != 0) return byScore;
            // 分数相同时按 ID 稳定排序，保证结果确定可复现
            return Long.compare(a.getKey(), b.getKey());
        });

        List<Long> result = new ArrayList<>(entries.size());
        for (Map.Entry<Long, Double> e : entries) {
            result.add(e.getKey());
            if (limit > 0 && result.size() >= limit) break;
        }
        return result;
    }

    /** 使用默认 k 融合。 */
    public List<Long> fuse(List<List<Long>> rankedLists, int limit) {
        return fuse(rankedLists, DEFAULT_K, limit);
    }
}
