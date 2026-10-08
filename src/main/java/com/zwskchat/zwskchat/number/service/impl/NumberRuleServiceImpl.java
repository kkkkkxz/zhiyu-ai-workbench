package com.zwskchat.zwskchat.number.service.impl;

import com.zwskchat.zwskchat.number.service.NumberRuleService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NumberRuleServiceImpl implements NumberRuleService {

    private static final String DEFAULT_DATE_PATTERN = "yyyyMMdd";
    private static final int DEFAULT_SEQ_LENGTH = 5;

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String nextNumber(String code, String defaultPrefix) {
        LoginUser user = SecurityUtils.currentUser();
        NumberRule rule = loadRule(user.getTenantId(), code, defaultPrefix);
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern(rule.datePattern()));
        ensureSequence(user.getTenantId(), code, dateKey);
        Long next = jdbcTemplate.queryForObject(
                "SELECT current_value FROM sys_number_sequence WHERE tenant_id=? AND code=? AND date_key=? FOR UPDATE",
                Long.class,
                user.getTenantId(), code, dateKey);
        long value = next == null ? 1L : next + 1L;
        jdbcTemplate.update(
                "UPDATE sys_number_sequence SET current_value=? WHERE tenant_id=? AND code=? AND date_key=?",
                value, user.getTenantId(), code, dateKey);
        return rule.prefix() + dateKey + "-" + String.format("%0" + rule.seqLength() + "d", value);
    }

    private NumberRule loadRule(Long tenantId, String code, String defaultPrefix) {
        Map<String, Object> row = jdbcTemplate.queryForList(
                "SELECT prefix,date_pattern,seq_length FROM sys_number_rule "
                        + "WHERE tenant_id=? AND code=? AND enabled=1 AND deleted=0 LIMIT 1",
                tenantId, code).stream().findFirst().orElse(null);
        if (row == null) {
            return new NumberRule(defaultPrefix, DEFAULT_DATE_PATTERN, DEFAULT_SEQ_LENGTH);
        }
        String prefix = String.valueOf(row.getOrDefault("prefix", defaultPrefix));
        String datePattern = String.valueOf(row.getOrDefault("date_pattern", DEFAULT_DATE_PATTERN));
        Number seqLength = (Number) row.getOrDefault("seq_length", DEFAULT_SEQ_LENGTH);
        return new NumberRule(prefix, datePattern, seqLength.intValue());
    }

    private void ensureSequence(Long tenantId, String code, String dateKey) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO sys_number_sequence(tenant_id, code, date_key, current_value) VALUES (?, ?, ?, 0)",
                tenantId, code, dateKey);
    }

    private record NumberRule(String prefix, String datePattern, int seqLength) {
    }
}
