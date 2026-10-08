package com.zwskchat.zwskchat.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("<script>SELECT * FROM knowledge_chunk WHERE deleted = 0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<KnowledgeChunk> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 关键词召回（混合检索的 BM25/keyword 分路）：在指定租户 + 知识库内，
     * 对 title / content 做 LIKE 匹配，命中任一关键词即返回，按 chunk_index 稳定排序。
     * 用于补齐向量召回易漏的型号、案例名、编号、法规条文等精确查找需求。
     */
    @Select("<script>SELECT * FROM knowledge_chunk WHERE deleted = 0 " +
            "AND tenant_id = #{tenantId} AND kb_id = #{kbId} " +
            "AND (" +
            "<foreach collection='terms' item='term' separator=' OR '>" +
            "title LIKE CONCAT('%', #{term}, '%') OR content LIKE CONCAT('%', #{term}, '%')" +
            "</foreach>" +
            ") ORDER BY chunk_index ASC LIMIT #{limit}</script>")
    List<KnowledgeChunk> searchByKeywords(@Param("tenantId") Long tenantId,
                                          @Param("kbId") Long kbId,
                                          @Param("terms") List<String> terms,
                                          @Param("limit") int limit);
}
