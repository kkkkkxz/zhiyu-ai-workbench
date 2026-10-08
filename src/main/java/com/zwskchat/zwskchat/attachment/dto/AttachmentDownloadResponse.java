package com.zwskchat.zwskchat.attachment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AttachmentDownloadResponse {
    private String url;
    private Integer expiresIn;
}
