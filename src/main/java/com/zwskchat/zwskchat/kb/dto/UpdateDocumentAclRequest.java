package com.zwskchat.zwskchat.kb.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class UpdateDocumentAclRequest {
    @Valid
    private List<DocumentAclItem> entries;
}
