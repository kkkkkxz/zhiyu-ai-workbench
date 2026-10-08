package com.zwskchat.zwskchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AccessDeniedHandlerImpl implements AccessDeniedHandler {

    private final ObjectMapper om;

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse resp, AccessDeniedException ex)
            throws IOException {
        resp.setStatus(HttpStatus.FORBIDDEN.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        om.writeValue(resp.getWriter(), Result.fail(ResultCode.FORBIDDEN));
    }
}
