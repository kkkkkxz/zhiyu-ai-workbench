package com.zwskchat.zwskchat.dispatch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchUserOption {
    private Long id;
    private Long deptId;
    private String username;
    private String nickname;
}
