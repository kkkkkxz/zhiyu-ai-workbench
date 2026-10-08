package com.zwskchat.zwskchat.tenant.dto;

import lombok.Data;

@Data
public class UpdateDepartmentRequest {
    private Long parentId;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private Long leaderUserId;
}
