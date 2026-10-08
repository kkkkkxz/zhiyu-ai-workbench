package com.zwskchat.zwskchat.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.notification.entity.SysNotification;
import com.zwskchat.zwskchat.notification.mapper.SysNotificationMapper;
import com.zwskchat.zwskchat.notification.service.NotificationService;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SysNotificationMapper notificationMapper;

    @Override
    public IPage<SysNotification> myNotifications(Page<SysNotification> page, Boolean unreadOnly) {
        Long userId = SecurityUtils.currentUser().getUserId();
        LambdaQueryWrapper<SysNotification> qw = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            qw.eq(SysNotification::getIsRead, 0);
        }
        qw.orderByDesc(SysNotification::getCreateTime);
        return notificationMapper.selectPage(page, qw);
    }

    @Override
    public long unreadCount() {
        Long userId = SecurityUtils.currentUser().getUserId();
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, userId)
                        .eq(SysNotification::getIsRead, 0));
    }

    @Override
    @Transactional
    public void markRead(Long id) {
        SysNotification n = notificationMapper.selectById(id);
        if (n != null) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    @Override
    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtils.currentUser().getUserId();
        notificationMapper.update(null,
                new LambdaUpdateWrapper<SysNotification>()
                        .eq(SysNotification::getUserId, userId)
                        .eq(SysNotification::getIsRead, 0)
                        .set(SysNotification::getIsRead, 1));
    }

    @Override
    @Transactional
    public void send(Long tenantId, Long userId, String type, String title, String content, String bizType, Long bizId) {
        SysNotification n = new SysNotification();
        n.setTenantId(tenantId);
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setBizType(bizType);
        n.setBizId(bizId);
        n.setIsRead(0);
        n.setChannel("web");
        n.setSendStatus("sent");
        notificationMapper.insert(n);
    }
}
