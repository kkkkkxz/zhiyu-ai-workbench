package com.zwskchat.zwskchat.attachment.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_attachment")
public class BizAttachment {
    private Long id;
    private Long tenantId;
    private String bizType;
    private Long bizId;
    private String category;
    private String fileName;
    private String fileKey;
    private String fileType;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private Integer versionNo;
    private String source;
    private String visibility;
    private Long uploaderId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
