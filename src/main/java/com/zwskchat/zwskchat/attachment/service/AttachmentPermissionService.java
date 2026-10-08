package com.zwskchat.zwskchat.attachment.service;

public interface AttachmentPermissionService {

    void assertReadable(String bizType, Long bizId);

    void assertWritable(String bizType, Long bizId);
}
