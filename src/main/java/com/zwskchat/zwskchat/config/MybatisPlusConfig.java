package com.zwskchat.zwskchat.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.zwskchat.zwskchat.config.properties.TenantProperties;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final TenantProperties tenantProps;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户拦截器（必须在分页之前）
        Set<String> ignoreSet = tenantProps.getIgnoreTables() == null
                ? new HashSet<>()
                : new HashSet<>(tenantProps.getIgnoreTables());
        log.info("[MyBatisPlus] tenant column={} ignore-tables={}", tenantProps.getColumn(), ignoreSet);
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                return new LongValue(TenantContext.requireTenantId());
            }

            @Override
            public String getTenantIdColumn() {
                return tenantProps.getColumn();
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (!tenantProps.isEnabled()) return true;
                if (TenantContext.isIgnore()) return true;
                return ignoreSet.contains(tableName);
            }
        }));
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 自动填充审计字段 */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            private static final Set<String> CREATE_TIME = Set.of("createTime");
            private static final Set<String> UPDATE_TIME = Set.of("updateTime");

            @Override
            public void insertFill(MetaObject mo) {
                LocalDateTime now = LocalDateTime.now();
                Long uid = SecurityUtils.currentUserIdOrNull();
                strictInsertFill(mo, "createTime", LocalDateTime.class, now);
                strictInsertFill(mo, "updateTime", LocalDateTime.class, now);
                if (uid != null) {
                    strictInsertFill(mo, "createBy", Long.class, uid);
                    strictInsertFill(mo, "updateBy", Long.class, uid);
                }
            }

            @Override
            public void updateFill(MetaObject mo) {
                strictUpdateFill(mo, "updateTime", LocalDateTime.class, LocalDateTime.now());
                Long uid = SecurityUtils.currentUserIdOrNull();
                if (uid != null) {
                    strictUpdateFill(mo, "updateBy", Long.class, uid);
                }
            }
        };
    }
}
