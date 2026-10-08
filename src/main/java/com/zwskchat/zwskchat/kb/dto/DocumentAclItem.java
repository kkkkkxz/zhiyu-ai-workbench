package com.zwskchat.zwskchat.kb.dto;

import com.zwskchat.zwskchat.kb.entity.KnowledgeDocumentAcl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentAclItem {
    private Long id;
    @NotBlank
    private String subjectType;
    @NotNull
    private Long subjectId;
    private String permission;

    public static DocumentAclItem from(KnowledgeDocumentAcl acl) {
        return DocumentAclItem.builder()
                .id(acl.getId())
                .subjectType(acl.getSubjectType())
                .subjectId(acl.getSubjectId())
                .permission(acl.getPermission())
                .build();
    }
}
