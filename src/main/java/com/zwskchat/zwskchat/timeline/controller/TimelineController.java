package com.zwskchat.zwskchat.timeline.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.timeline.entity.BizTimelineEvent;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "业务时间线")
@RestController
@RequestMapping("/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @Operation(summary = "查询业务时间线")
    @GetMapping("/{bizType}/{bizId}")
    public Result<List<BizTimelineEvent>> list(
            @PathVariable String bizType,
            @PathVariable Long bizId) {
        return Result.success(timelineService.listByBiz(bizType, bizId));
    }
}
