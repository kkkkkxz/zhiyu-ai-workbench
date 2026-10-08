package com.zwskchat.zwskchat.template.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tpl_install_record")
public class TplInstallRecord {
    private Long id;
    private Long tenantId;
    private String packageCode;
    private String packageName;
    private String version;
    private String status;
    private String configJson;
    private Long installedBy;
    private LocalDateTime installedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
