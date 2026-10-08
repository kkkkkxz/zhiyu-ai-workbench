package com.zwskchat.zwskchat.datascope.service;

import com.zwskchat.zwskchat.datascope.entity.SysDataScope;

import java.util.List;
import java.util.Set;

public interface DataScopeService {

    DataScopeResult resolve(String bizType);

    List<SysDataScope> listByRole(Long roleId);

    void save(SysDataScope dataScope);

    void delete(Long id);

    record DataScopeResult(String scopeType, Set<Long> visibleDeptIds, Long userId) {

        public boolean isAll() {
            return "all".equals(scopeType);
        }

        public boolean isSelfOnly() {
            return "self".equals(scopeType);
        }
    }
}
