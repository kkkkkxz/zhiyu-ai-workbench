package com.zwskchat.zwskchat.ai.hub;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 业务动作统一返回结构
 */
@Data
public class BizActionResult {
    private BizActionType actionType;
    private boolean needConfirm;
    private String confirmMessage;
    private Map<String, Object> previewData;

    // 查询类结果
    private boolean queryResult;
    private String summary;
    private List<String> columns;
    private List<Map<String, Object>> rows;

    // 操作成功后的结果
    private boolean executed;
    private String successMessage;

    public static BizActionResult confirm(BizActionType type, String message, Map<String, Object> data) {
        BizActionResult r = new BizActionResult();
        r.actionType = type;
        r.needConfirm = true;
        r.confirmMessage = message;
        r.previewData = data;
        return r;
    }

    public static BizActionResult query(BizActionType type, String summary, List<String> columns, List<Map<String, Object>> rows) {
        BizActionResult r = new BizActionResult();
        r.actionType = type;
        r.queryResult = true;
        r.summary = summary;
        r.columns = columns;
        r.rows = rows;
        return r;
    }

    public static BizActionResult success(BizActionType type, String message) {
        BizActionResult r = new BizActionResult();
        r.actionType = type;
        r.executed = true;
        r.successMessage = message;
        return r;
    }

    public static BizActionResult error(String message) {
        BizActionResult r = new BizActionResult();
        r.actionType = BizActionType.UNKNOWN;
        r.summary = message;
        return r;
    }
}
