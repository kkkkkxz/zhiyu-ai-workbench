package com.zwskchat.zwskchat.datascope.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.datascope.entity.SysDataScope;
import com.zwskchat.zwskchat.datascope.service.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据权限范围")
@RestController
@RequestMapping("/data-scope")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class DataScopeController {

    private final DataScopeService dataScopeService;

    @Operation(summary = "查询角色的数据权限配置")
    @GetMapping("/role/{roleId}")
    public Result<List<SysDataScope>> listByRole(@PathVariable Long roleId) {
        return Result.success(dataScopeService.listByRole(roleId));
    }

    @Operation(summary = "保存数据权限配置")
    @PostMapping
    public Result<Void> save(@RequestBody SysDataScope dataScope) {
        dataScopeService.save(dataScope);
        return Result.success(null);
    }

    @Operation(summary = "删除数据权限配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataScopeService.delete(id);
        return Result.success(null);
    }
}
