package com.zwskchat.zwskchat.export.constant;

public final class ExportConstants {

    private ExportConstants() {
    }

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public static final String FORMAT_PDF = "pdf";
    public static final String FORMAT_DOCX = "docx";

    public static final String BIZ_TYPE_CUSTOMER = "customer";
    public static final String BIZ_TYPE_CONTRACT = "contract";
    public static final String BIZ_TYPE_WORK_ORDER = "work_order";
    public static final String BIZ_TYPE_SESSION = "session";

    public static final String EXPORT_QUEUE = "zwsk.export.queue";
    public static final String EXPORT_EXCHANGE = "zwsk.export.exchange";
    public static final String EXPORT_ROUTING_KEY = "zwsk.export";
    public static final String EXPORT_DLX = "zwsk.export.dlx";
    public static final String EXPORT_DLQ = "zwsk.export.dlq";
    public static final String EXPORT_DLQ_ROUTING_KEY = "zwsk.export.dlq";

    public static final int DOWNLOAD_URL_EXPIRES_IN_SECONDS = 600;
}
