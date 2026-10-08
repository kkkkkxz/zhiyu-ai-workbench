package com.zwskchat.zwskchat.kb.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.kb.dto.DocumentAclItem;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentAclRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeDocumentAcl;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentAclMapper;
import com.zwskchat.zwskchat.kb.service.DocumentAclService;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentAclServiceImpl implements DocumentAclService {

    private final KnowledgeDocumentAclMapper aclMapper;

    @Override
    public List<DocumentAclItem> listByDoc(Long docId) {
        List<KnowledgeDocumentAcl> list = aclMapper.selectList(Wrappers.<KnowledgeDocumentAcl>lambdaQuery()
                .eq(KnowledgeDocumentAcl::getDocId, docId)
                .eq(KnowledgeDocumentAcl::getDeleted, 0));
        return list.stream().map(DocumentAclItem::from).toList();
    }

    @Override
    @Transactional
    public void updateAcl(Long docId, UpdateDocumentAclRequest request) {
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        // 先清除旧 ACL
        aclMapper.delete(Wrappers.<KnowledgeDocumentAcl>lambdaQuery()
                .eq(KnowledgeDocumentAcl::getDocId, docId));
        // 批量插入新 ACL
        if (request.getEntries() != null) {
            for (DocumentAclItem item : request.getEntries()) {
                KnowledgeDocumentAcl acl = new KnowledgeDocumentAcl();
                acl.setTenantId(tenantId);
                acl.setDocId(docId);
                acl.setSubjectType(item.getSubjectType());
                acl.setSubjectId(item.getSubjectId());
                acl.setPermission(item.getPermission() == null ? "READ" : item.getPermission());
                aclMapper.insert(acl);
            }
        }
    }

    @Override
    public boolean canRead(Long docId, Long userId, Long deptId, List<Long> managerUserIds) {
        List<KnowledgeDocumentAcl> aclList = aclMapper.selectList(Wrappers.<KnowledgeDocumentAcl>lambdaQuery()
                .eq(KnowledgeDocumentAcl::getDocId, docId)
                .eq(KnowledgeDocumentAcl::getDeleted, 0));
        if (aclList == null || aclList.isEmpty()) {
            return true;
        }
        for (KnowledgeDocumentAcl acl : aclList) {
            switch (acl.getSubjectType()) {
                case "USER":
                    if (acl.getSubjectId().equals(userId)) return true;
                    break;
                case "DEPT":
                    if (deptId != null && acl.getSubjectId().equals(deptId)) return true;
                    break;
                case "REPORTING":
                    if (managerUserIds != null && managerUserIds.contains(acl.getSubjectId())) return true;
                    break;
            }
        }
        return false;
    }
}
