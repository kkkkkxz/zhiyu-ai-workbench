package com.zwskchat.zwskchat.finetune;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.security.annotation.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练数据导出（管理员）：把会话沉淀为可用于 SFT 的 JSONL，支撑模型微调流水线。
 * 对应工程计划书“模型微调”中“产出可训练数据”的应用内落地。
 */
@Tag(name = "模型微调-训练数据")
@RestController
@RequestMapping("/admin/training-data")
@RequiredArgsConstructor
@AdminOnly
public class TrainingDataController {

    private final TrainingDataService trainingDataService;

    @Operation(summary = "导出 SFT 训练数据（ShareGPT 风格 JSONL）",
            description = "onlyPositive=true 时仅导出获得点赞的高质量问答；sessionId 可选，仅导出单会话。")
    @GetMapping("/sft")
    @AuditLog(operationType = "FINETUNE_EXPORT_SFT", targetType = "training_data")
    public ResponseEntity<String> exportSft(@RequestParam(required = false) Long sessionId,
                                            @RequestParam(defaultValue = "false") boolean onlyPositive,
                                            @RequestParam(required = false) String systemPrompt) {
        String jsonl = trainingDataService.exportSftJsonl(sessionId, onlyPositive, systemPrompt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sft-train.jsonl\"")
                .contentType(MediaType.parseMediaType("application/jsonl"))
                .body(jsonl);
    }
}
