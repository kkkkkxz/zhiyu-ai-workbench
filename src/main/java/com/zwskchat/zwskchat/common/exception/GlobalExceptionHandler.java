package com.zwskchat.zwskchat.common.exception;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        log.warn("[Business] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.ok(Result.fail(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleParam(Exception ex) {
        String msg = ex instanceof MethodArgumentNotValidException me && me.getBindingResult().getFieldError() != null
                ? me.getBindingResult().getFieldError().getDefaultMessage()
                : "请求参数不合法";
        return ResponseEntity.badRequest().body(Result.fail(ResultCode.BAD_REQUEST, msg));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ResultCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.fail(ResultCode.FILE_TOO_LARGE));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthn(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(ResultCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAuthz(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(ResultCode.FORBIDDEN));
    }

    @ExceptionHandler(java.io.IOException.class)
    public void handleIoException(java.io.IOException ex, HttpServletRequest req) {
        // SSE 流式响应中客户端断开连接是正常场景，仅记录 info 级别日志
        if (ex.getMessage() != null && ex.getMessage().contains("已建立的连接")) {
            log.info("[SSE] Client disconnected: {} {}", req.getMethod(), req.getRequestURI());
        } else {
            log.warn("[IO] {} {} - {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        }
        // 不返回响应体，因为连接已断开或响应已提交
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception ex, HttpServletRequest req) {
        // 注意：堆栈仅落服务端日志，不返回给前端
        log.error("[Unexpected] {} {}", req.getMethod(), req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ResultCode.INTERNAL_ERROR));
    }
}
