package com.zwskchat.zwskchat.sso;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoTicketServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final SsoTicketService service = new SsoTicketService(redis);

    @Test
    void issueStoresTenantAndUserWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String ticket = service.issue(7L, 42L);

        assertNotNull(ticket);
        assertTrue(ticket.length() >= 16);
        // 值为 "tenantId:userId"，TTL 为约定的短有效期
        verify(valueOps).set(anyString(), eq("7:42"), eq(Duration.ofSeconds(SsoTicketService.TTL_SECONDS)));
    }

    @Test
    void issueRejectsNulls() {
        assertThrows(IllegalArgumentException.class, () -> service.issue(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.issue(1L, null));
    }

    @Test
    void consumeReturnsPrincipalAndIsOneTime() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(any())).thenReturn("7:42");

        SsoTicketService.SsoPrincipal p = service.consume("abc");

        assertNotNull(p);
        assertEquals(7L, p.tenantId());
        assertEquals(42L, p.userId());
    }

    @Test
    void consumeReturnsNullForMissingTicket() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(any())).thenReturn(null);

        assertNull(service.consume("missing"));
    }

    @Test
    void consumeReturnsNullForBlankOrMalformed() {
        assertNull(service.consume(null));
        assertNull(service.consume(" "));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(any())).thenReturn("garbage-no-colon");
        assertNull(service.consume("x"));
    }
}
