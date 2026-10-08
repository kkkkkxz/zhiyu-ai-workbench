package com.zwskchat.zwskchat.notification.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.notification.entity.SysNotification;

public interface NotificationService {
    IPage<SysNotification> myNotifications(Page<SysNotification> page, Boolean unreadOnly);
    long unreadCount();
    void markRead(Long id);
    void markAllRead();
    void send(Long tenantId, Long userId, String type, String title, String content, String bizType, Long bizId);
}
