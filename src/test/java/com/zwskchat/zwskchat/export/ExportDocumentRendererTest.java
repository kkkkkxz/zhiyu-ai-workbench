package com.zwskchat.zwskchat.export;

import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.export.dto.ExportReport;
import com.zwskchat.zwskchat.export.dto.ExportSection;
import com.zwskchat.zwskchat.export.service.ExportDocumentRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportDocumentRendererTest {

    private final ExportDocumentRenderer renderer = new ExportDocumentRenderer();

    @Test
    void exposesSupportedFormats() {
        assertThat(renderer.contentType("docx"))
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(renderer.extension("pdf")).isEqualTo("pdf");
    }

    @Test
    void rejectsUnsupportedFormat() {
        ExportReport report = new ExportReport("测试", "副标题",
                List.of(new ExportSection("摘要", List.of("内容"))));
        assertThatThrownBy(() -> renderer.render(report, "xlsx"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void formatsNullValues() {
        assertThat(renderer.formatValue(null)).isEqualTo("-");
    }
}
