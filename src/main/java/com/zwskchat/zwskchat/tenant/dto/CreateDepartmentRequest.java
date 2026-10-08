package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDepartmentRequest {
    private Long parentId;
    @NotBlank
    private String name;
    private String code;
    private Integer sort;
    private Long leaderUserId;
}
