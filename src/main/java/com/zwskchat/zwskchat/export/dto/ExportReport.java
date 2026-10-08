package com.zwskchat.zwskchat.export.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportReport {

    private String title;
    private String subtitle;
    private List<ExportSection> sections;
}
