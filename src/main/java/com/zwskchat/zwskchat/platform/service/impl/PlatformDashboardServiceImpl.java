package com.zwskchat.zwskchat.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeBaseMapper;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentMapper;
import com.zwskchat.zwskchat.platform.dto.PlatformOverviewResponse;
import com.zwskchat.zwskchat.platform.dto.TenantRankingResponse;
import com.zwskchat.zwskchat.platform.service.PlatformDashboardService;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.entity.AdminApply;
import com.zwskchat.zwskchat.tenant.mapper.AdminApplyMapper;
import com.zwskchat.zwskchat.user.entity.SysDept;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysDeptMapper;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlatformDashboardServiceImpl implements PlatformDashboardService {

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final AiMessageMapper messageMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final AdminApplyMapper adminApplyMapper;

    @Override
    public PlatformOverviewResponse getOverview() {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            long tenantTotal = tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
                    .eq(SysTenant::getDeleted, 0));
            long activeTenantTotal = tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery()
                    .eq(SysTenant::getStatus, 1)
                    .eq(SysTenant::getDeleted, 0));
            long userTotal = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getDeleted, 0));

            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            long todayActiveUserTotal = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                    .ge(SysUser::getLastLoginTime, todayStart)
                    .eq(SysUser::getDeleted, 0));

            long todayChatTotal = messageMapper.selectCount(Wrappers.<AiMessage>lambdaQuery()
                    .ge(AiMessage::getCreateTime, todayStart)
                    .eq(AiMessage::getRole, "user"));

            long kbTotal = kbMapper.selectCount(null);
            long documentTotal = documentMapper.selectCount(null);

            long departmentTotal = deptMapper.selectCount(Wrappers.<SysDept>lambdaQuery()
                    .eq(SysDept::getDeleted, 0));

            long pendingAdminApplyTotal = adminApplyMapper.selectCount(Wrappers.<AdminApply>lambdaQuery()
                    .eq(AdminApply::getStatus, "PENDING")
                    .eq(AdminApply::getDeleted, 0));

            long todayTokenTotal = 0;
            List<AiMessage> todayMessages = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                    .ge(AiMessage::getCreateTime, todayStart)
                    .eq(AiMessage::getRole, "assistant")
                    .select(AiMessage::getTokenIn, AiMessage::getTokenOut));
            for (AiMessage m : todayMessages) {
                if (m.getTokenIn() != null) todayTokenTotal += m.getTokenIn();
                if (m.getTokenOut() != null) todayTokenTotal += m.getTokenOut();
            }

            return PlatformOverviewResponse.builder()
                    .tenantTotal(tenantTotal)
                    .activeTenantTotal(activeTenantTotal)
                    .userTotal(userTotal)
                    .todayActiveUserTotal(todayActiveUserTotal)
                    .todayChatTotal(todayChatTotal)
                    .kbTotal(kbTotal)
                    .documentTotal(documentTotal)
                    .departmentTotal(departmentTotal)
                    .pendingAdminApplyTotal(pendingAdminApplyTotal)
                    .todayTokenTotal(todayTokenTotal)
                    .todayErrorTotal(0)
                    .build();
        }
    }

    @Override
    public List<TenantRankingResponse> getTenantRanking(int limit) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                    .eq(SysTenant::getDeleted, 0)
                    .orderByDesc(SysTenant::getCreateTime)
                    .last("LIMIT " + Math.min(limit, 50)));

            List<SysUser> allUsers = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getDeleted, 0)
                    .select(SysUser::getTenantId));

            Map<Long, Long> userCountByTenant = allUsers.stream()
                    .collect(Collectors.groupingBy(SysUser::getTenantId, Collectors.counting()));

            List<TenantRankingResponse> result = new ArrayList<>();
            for (SysTenant t : tenants) {
                result.add(TenantRankingResponse.builder()
                        .tenantId(t.getId())
                        .tenantName(t.getName())
                        .tenantCode(t.getCode())
                        .status(t.getStatus())
                        .userTotal(userCountByTenant.getOrDefault(t.getId(), 0L))
                        .chatTotal(0)
                        .kbTotal(0)
                        .documentTotal(0)
                        .tokenTotal(0)
                        .lastActiveTime(t.getUpdateTime())
                        .build());
            }
            return result;
        }
    }
}
