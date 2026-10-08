package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.user.entity.SysDept;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DepartmentResponse {
    private Long id;
    private Long tenantId;
    private Long parentId;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
    private Long leaderUserId;
    private String path;
    private Integer level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Builder.Default
    private List<DepartmentResponse> children = new ArrayList<>();

    public static DepartmentResponse from(SysDept dept) {
        return DepartmentResponse.builder()
                .id(dept.getId())
                .tenantId(dept.getTenantId())
                .parentId(dept.getParentId())
                .name(dept.getName())
                .code(dept.getCode())
                .sort(dept.getSort())
                .status(dept.getStatus())
                .leaderUserId(dept.getLeaderUserId())
                .path(dept.getPath())
                .level(dept.getLevel())
                .createTime(dept.getCreateTime())
                .updateTime(dept.getUpdateTime())
                .build();
    }
}
