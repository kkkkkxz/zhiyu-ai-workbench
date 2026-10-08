package com.zwskchat.zwskchat.template.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.template.entity.TplInstallRecord;
import com.zwskchat.zwskchat.template.entity.TplPackage;
import com.zwskchat.zwskchat.template.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "模板中心")
@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "可用模板列表")
    @GetMapping
    public Result<List<TplPackage>> listPackages() {
        return Result.success(templateService.listPackages());
    }

    @Operation(summary = "当前租户已安装模板")
    @GetMapping("/installed")
    public Result<List<TplInstallRecord>> listInstalled() {
        return Result.success(templateService.listInstalled());
    }

    @Operation(summary = "安装模板")
    @PostMapping("/{packageCode}/install")
    public Result<TplInstallRecord> install(@PathVariable String packageCode) {
        return Result.success(templateService.install(packageCode));
    }
}
