package com.zwskchat.zwskchat.rag.ops;

import com.zwskchat.zwskchat.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops/rag")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class RagOpsController {

    private final RagReindexService reindexService;

    @PostMapping("/reindex-existing-chunks")
    public Result<RagReindexService.ReindexResult> reindexExistingChunks(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return Result.success(reindexService.reindexExistingChunks(tenantId, kbId, limit, dryRun));
    }
}
