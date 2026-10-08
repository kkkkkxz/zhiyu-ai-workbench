package com.zwskchat.zwskchat.common.api;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {
    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.total = total;
        p.page = page;
        p.size = size;
        p.records = records == null ? Collections.emptyList() : records;
        return p;
    }
}
