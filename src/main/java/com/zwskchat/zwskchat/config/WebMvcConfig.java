package com.zwskchat.zwskchat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局配置。
 *
 * <p><b>关于 CORS</b>：本类不再实现 addCorsMappings。CORS 统一由
 * {@link com.zwskchat.zwskchat.security.SecurityConfig} 通过
 * {@code http.cors().configurationSource(...)} 集中处理，origin 列表由
 * {@code zwsk.security.cors.allowed-origins} 配置驱动。</p>
 *
 * <p>历史教训：Spring Security 的 CorsFilter 与 WebMvc 的 CorsRegistry 是两套独立机制。
 * 如果在这里再写 {@code allowedOriginPatterns("*")} 等于给整个站点开了不收口的 CORS，
 * 等同于绕过 SecurityProperties.cors 的精细化白名单——任何来源都能带 Cookie 拿响应，
 * 加上 {@code allowCredentials(true)} 后还会触发 CSRF 风险。生产事故案例非常多。</p>
 *
 * <p>需要补 WebSocket 之外的全局 MVC 配置（如静态资源映射、拦截器）时，再实现对应方法即可。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}
