package com.zwskchat.zwskchat.template.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tpl_package")
public class TplPackage {
    private Long id;
    private String code;
    private String name;
    private String category;
    private String description;
    private String scenario;
    private String modulesJson;
    private String aiAgentsJson;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
