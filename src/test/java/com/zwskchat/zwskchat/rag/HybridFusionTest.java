package com.zwskchat.zwskchat.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridFusionTest {

    private final HybridFusion fusion = new HybridFusion();

    @Test
    void itemRankedHighInBothListsWinsByRrf() {
        // 100 在两路都靠前 -> RRF 最高
        List<Long> dense = List.of(100L, 1L, 2L, 3L);
        List<Long> keyword = List.of(100L, 9L, 8L);

        List<Long> fused = fusion.fuse(List.of(dense, keyword), 0);

        assertEquals(100L, fused.get(0));
    }

    @Test
    void mergesUniqueIdsFromAllLists() {
        List<Long> dense = List.of(1L, 2L);
        List<Long> keyword = List.of(3L, 4L);

        List<Long> fused = fusion.fuse(List.of(dense, keyword), 0);

        assertTrue(fused.containsAll(List.of(1L, 2L, 3L, 4L)));
        assertEquals(4, fused.size());
    }

    @Test
    void crossListAgreementBeatsSingleListTop() {
        // dense 第一是 1；但 50 在两路都出现（rank2 + rank1），RRF 应让 50 反超 1
        List<Long> dense = List.of(1L, 50L);
        List<Long> keyword = List.of(50L, 2L);

        List<Long> fused = fusion.fuse(List.of(dense, keyword), 0);

        assertEquals(50L, fused.get(0), "两路共识项应排第一");
    }

    @Test
    void respectsLimit() {
        List<Long> dense = List.of(1L, 2L, 3L, 4L, 5L);
        List<Long> fused = fusion.fuse(List.of(dense), 3);
        assertEquals(3, fused.size());
    }

    @Test
    void handlesNullAndEmptyListsSafely() {
        List<Long> fused = fusion.fuse(null, 0);
        assertTrue(fused.isEmpty());

        List<Long> withNulls = fusion.fuse(java.util.Arrays.asList(List.of(1L), null), 0);
        assertEquals(List.of(1L), withNulls);
    }

    @Test
    void deterministicOrderingOnTie() {
        // 单路两项分数不同，但构造平局：两路完全相同 -> 各自 rank 贡献相同
        List<Long> a = List.of(7L, 8L);
        List<Long> b = List.of(8L, 7L);
        // 7: 1/(60+1)+1/(60+2) ; 8: 1/(60+2)+1/(60+1) -> 相等，按 ID 升序
        List<Long> fused = fusion.fuse(List.of(a, b), 0);
        assertEquals(List.of(7L, 8L), fused);
    }
}
