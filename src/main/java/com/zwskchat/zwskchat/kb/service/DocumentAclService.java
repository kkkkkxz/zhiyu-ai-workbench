package com.zwskchat.zwskchat.kb.service;

import com.zwskchat.zwskchat.kb.dto.DocumentAclItem;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentAclRequest;

import java.util.List;

public interface DocumentAclService {

    List<DocumentAclItem> listByDoc(Long docId);

    void updateAcl(Long docId, UpdateDocumentAclRequest request);

    boolean canRead(Long docId, Long userId, Long deptId, List<Long> managerUserIds);
}
