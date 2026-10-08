package com.zwskchat.zwskchat.export.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportDownloadResponse {

    private String url;
    private Integer expiresIn;
}
