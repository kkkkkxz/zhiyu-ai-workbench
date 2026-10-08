package com.zwskchat.zwskchat.export.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.export.dto.ExportDownloadResponse;
import com.zwskchat.zwskchat.export.entity.ExportTask;
import com.zwskchat.zwskchat.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文件导出")
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Operation(summary = "发起导出")
    @PostMapping
    public Result<ExportTask> create(
            @RequestParam String bizType,
            @RequestParam(required = false) Long bizId,
            @RequestParam(defaultValue = "pdf") String format) {
        return Result.success(exportService.createTask(bizType, bizId, format));
    }

    @Operation(summary = "导出任务详情")
    @GetMapping("/{id}")
    public Result<ExportTask> get(@PathVariable Long id) {
        return Result.success(exportService.getById(id));
    }

    @Operation(summary = "获取下载链接")
    @GetMapping("/{id}/download-url")
    public Result<ExportDownloadResponse> downloadUrl(@PathVariable Long id) {
        return Result.success(exportService.createDownloadUrl(id));
    }

    @Operation(summary = "我的导出任务")
    @GetMapping
    public Result<IPage<ExportTask>> myTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(exportService.myTasks(new Page<>(page, size)));
    }
}
