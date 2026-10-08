package com.zwskchat.zwskchat.ai.hub;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSafeSchemaProviderTest {

    private final TenantSafeSchemaProvider provider = new TenantSafeSchemaProvider();

    @Test
    void acceptsAllowedTenantScopedBusinessQuery() {
        assertThat(provider.isTenantSafe(
                "select id, name from biz_customer where tenant_id = 1 and deleted = 0",
                1L)).isTrue();
    }

    @Test
    void rejectsTenantBypassWithOrCondition() {
        assertThat(provider.isTenantSafe(
                "select id, name from biz_customer where tenant_id = 1 or 1 = 1",
                1L)).isFalse();
    }

    @Test
    void rejectsUnionAndSystemTableQuery() {
        assertThat(provider.isTenantSafe(
                "select id from biz_customer where tenant_id = 1 union select id from sys_user",
                1L)).isFalse();
    }

    @Test
    void rejectsUnknownTableEvenWithTenantCondition() {
        assertThat(provider.isTenantSafe(
                "select id from sys_user where tenant_id = 1",
                1L)).isFalse();
    }

    @Test
    void requiresDispatchAssigneeToJoinDispatchMainTable() {
        assertThat(provider.isTenantSafe(
                "select user_id from biz_dispatch_assignee where tenant_id = 1",
                1L)).isFalse();
        assertThat(provider.isTenantSafe(
                "select a.user_id from biz_dispatch_assignee a join biz_dispatch d on a.dispatch_id = d.id where d.tenant_id = 1",
                1L)).isTrue();
    }
}
