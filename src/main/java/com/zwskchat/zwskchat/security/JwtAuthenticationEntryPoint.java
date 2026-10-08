package com.zwskchat.zwskchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper om;

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse resp, AuthenticationException ex)
            throws IOException {
        resp.setStatus(HttpStatus.UNAUTHORIZED.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        om.writeValue(resp.getWriter(), Result.fail(ResultCode.UNAUTHORIZED));
    }
}
