package com.zwskchat.zwskchat.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.audit.entity.AuditLogEntity;
import com.zwskchat.zwskchat.audit.service.AuditLogService;
import com.zwskchat.zwskchat.common.util.IpUtils;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditService;
    private final ObjectMapper om;
    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(audit)")
    public Object around(ProceedingJoinPoint pjp, AuditLog audit) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            try {
                save(pjp, audit, result, error, System.currentTimeMillis() - start);
            } catch (Exception ignore) {
                // 审计失败不能影响主流程
            }
        }
    }

    private void save(ProceedingJoinPoint pjp, AuditLog audit, Object ret, Throwable err, long cost) {
        AuditLogEntity log = new AuditLogEntity();
        log.setOperationType(audit.operationType());
        log.setTargetType(audit.targetType().isBlank() ? null : audit.targetType());
        log.setResult(err == null ? "SUCCESS" : "FAILED");
        log.setCostMs((int) Math.min(cost, Integer.MAX_VALUE));

        LoginUser u = SecurityUtils.currentUserOrNull();
        if (u != null) {
            log.setOperatorId(u.getUserId());
            log.setOperatorName(u.getUsername());
            log.setTenantId(u.getTenantId());
        }

        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            HttpServletRequest req = attr.getRequest();
            log.setIp(IpUtils.getClientIp(req));
            log.setUserAgent(safe(req.getHeader("User-Agent"), 500));
            log.setRequestUri(safe(req.getRequestURI(), 500));
            log.setRequestMethod(req.getMethod());
        }

        // SpEL 解析 targetId。
        // 用 SimpleEvaluationContext.forReadOnlyDataBinding() 而非 StandardEvaluationContext：
        // 前者只允许 property 读取（如 #ret.data.id），禁止任意 Java 方法 / 类型构造调用。
        // 当前 targetIdExpr 都来自硬编码注解（如 "#kbId" / "#ret.data?.id"），切到只读
        // 数据绑定模式仍能正常工作；同时把"未来如果有人误把用户输入接到 SpEL"的攻击面收敛掉。
        if (!audit.targetIdExpr().isBlank()) {
            try {
                MethodSignature sig = (MethodSignature) pjp.getSignature();
                Method m = sig.getMethod();
                EvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
                Parameter[] ps = m.getParameters();
                Object[] args = pjp.getArgs();
                for (int i = 0; i < ps.length; i++) {
                    ctx.setVariable(ps[i].getName(), args[i]);
                }
                ctx.setVariable("ret", ret);
                Expression exp = parser.parseExpression(audit.targetIdExpr());
                Object v = exp.getValue(ctx);
                if (v != null) log.setTargetId(String.valueOf(v));
            } catch (Exception e) {
                AuditLogAspect.logger().debug("[Audit] SpEL parse failed: {}", e.getMessage());
            }
        }

        if (audit.recordArgs()) {
            try {
                String json = om.writeValueAsString(pjp.getArgs());
                log.setDetail(safe(json, 4000));
            } catch (Exception ignore) {}
        }
        if (err != null) {
            String msg = err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage();
            log.setDetail(safe((log.getDetail() == null ? "" : log.getDetail() + " | ") + "ERR:" + msg, 4000));
        }

        auditService.asyncSave(log);
    }

    private static org.slf4j.Logger logger() { return org.slf4j.LoggerFactory.getLogger(AuditLogAspect.class); }

    private static String safe(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
