package com.zwskchat.zwskchat.kb.service;

import com.zwskchat.zwskchat.kb.dto.CreateKbRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBase create(CreateKbRequest req);

    KnowledgeBase getById(Long id);

    List<KnowledgeBase> listMine();

    void delete(Long id);

    /** 检查当前用户是否可读该知识库（考虑 visibility / owner）。 */
    void assertReadable(Long kbId);

    /** 检查当前用户是否可写入（上传 / 修改）。 */
    void assertWritable(Long kbId);

    /** 为“静默”场景（VoiceOrchestratorV3 异步任务）使用：返回当前用户可读的 kbId。 */
    KnowledgeBase getReadable(Long kbId);
}
