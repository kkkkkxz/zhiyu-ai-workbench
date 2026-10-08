package com.zwskchat.zwskchat.dispatch.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchDeptOption {
    private Long id;
    private String name;
    private String code;
}
