package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.tenant")
public class TenantProperties {
    private boolean enabled = true;
    private String column = "tenant_id";
    /** 不参与租户隔离的表（系统级表，全租户共享） */
    private List<String> ignoreTables = new ArrayList<>();
}
