package com.zwskchat.zwskchat.kb.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.kb.dto.CreateKbRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeBase;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeBaseMapper;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /**
     * KB 维度的"管理员角色"白名单：与 V1.0/V1.2 SQL 中实际存在的角色码对齐。
     * <p>历史 bug：曾经只检查 {@code "ADMIN"} / {@code "SUPER_ADMIN"} 两个角色，
     * 而数据库里实际定义的是 {@code SUPER_ADMIN / TENANT_ADMIN / KB_ADMIN / STAFF / AUDITOR}，
     * 导致 TENANT_ADMIN / KB_ADMIN 形同虚设——既不能删别人的 KB，也不能写入。
     * 修正后这两个角色与 SUPER_ADMIN 一样，对本租户内的 KB 拥有管理权。</p>
     */
    private static final Set<String> KB_ADMIN_ROLES =
            Set.of("SUPER_ADMIN", "TENANT_ADMIN", "KB_ADMIN");

    private final KnowledgeBaseMapper kbMapper;

    @Override
    public KnowledgeBase create(CreateKbRequest req) {
        Long uid = SecurityUtils.currentUserId();
        Long tid = SecurityUtils.currentUser().getTenantId();
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(tid);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setOwnerId(uid);
        kb.setVisibility(req.getVisibility() == null ? "PRIVATE" : req.getVisibility());
        kb.setStatus(1);
        kbMapper.insert(kb);
        return kb;
    }

    @Override
    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) throw new BusinessException(ResultCode.KB_NOT_FOUND);
        return kb;
    }

    @Override
    public List<KnowledgeBase> listMine() {
        // 多租户拦截器自动追加 tenant_id；MyBatis-Plus 逻辑删除自动加 deleted=0
        return kbMapper.selectList(Wrappers.<KnowledgeBase>lambdaQuery()
                .orderByDesc(KnowledgeBase::getUpdateTime));
    }

    @Override
    public void delete(Long id) {
        KnowledgeBase kb = getById(id);
        Long uid = SecurityUtils.currentUserId();
        if (!kb.getOwnerId().equals(uid)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅所有者可删除知识库");
        }
        kbMapper.deleteById(id);
    }

    @Override
    public void assertReadable(Long kbId) {
        getReadable(kbId);
    }

    @Override
    public KnowledgeBase getReadable(Long kbId) {
        KnowledgeBase kb = getById(kbId);
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new BusinessException(ResultCode.KB_NO_PERMISSION, "知识库已停用");
        }
        LoginUser u = SecurityUtils.currentUser();
        // 租户边界：MyBatis-Plus 拦截器已过滤 tenant_id，但这里多一道防线。
        if (kb.getTenantId() != null && !kb.getTenantId().equals(u.getTenantId())) {
            throw new BusinessException(ResultCode.KB_NO_PERMISSION, "跨租户访问被拒绝");
        }
        // 白名单式可见性判定：只有显式 PUBLIC 才允许任意同租户用户读；
        // PRIVATE / DEPT / ROLE / USER 在 ACL 表实现之前一律按 PRIVATE 处理（仅 owner / admin 可读），
        // 防御 visibility 字段被误填成 'DEPT' / 'public ' / 大小写变体后变成"事实公开"的越权风险。
        if (!isPublic(kb) && !isOwner(kb, u) && !isAdmin(u)) {
            throw new BusinessException(ResultCode.KB_NO_PERMISSION, "无权访问该知识库");
        }
        return kb;
    }

    @Override
    public void assertWritable(Long kbId) {
        KnowledgeBase kb = getById(kbId);
        if (kb.getStatus() == null || kb.getStatus() != 1) {
            throw new BusinessException(ResultCode.KB_NO_PERMISSION, "知识库已停用");
        }
        LoginUser u = SecurityUtils.currentUser();
        if (kb.getTenantId() != null && !kb.getTenantId().equals(u.getTenantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "跨租户写入被拒绝");
        }
        if (!isOwner(kb, u) && !isAdmin(u)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅知识库拥有者或管理员可写入");
        }
    }

    /**
     * 是否为"完全公开"知识库——任何同租户登录用户都可读。
     * 白名单式判定：仅 visibility 严格等于 "PUBLIC"（忽略大小写）才公开；
     * PRIVATE / DEPT / ROLE / USER / null / 未知值统一视为受限，由调用方走 owner / admin / ACL 判定。
     */
    private static boolean isPublic(KnowledgeBase kb) {
        return "PUBLIC".equalsIgnoreCase(kb.getVisibility());
    }

    private static boolean isOwner(KnowledgeBase kb, LoginUser u) {
        return kb.getOwnerId() != null && kb.getOwnerId().equals(u.getUserId());
    }

    private static boolean isAdmin(LoginUser u) {
        Set<String> roles = u.getRoles();
        if (roles == null || roles.isEmpty()) return false;
        for (String r : roles) {
            if (r != null && KB_ADMIN_ROLES.contains(r)) return true;
        }
        return false;
    }
}
