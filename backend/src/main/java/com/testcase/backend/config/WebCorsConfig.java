package com.testcase.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 横切配置：
 * <ul>
 *   <li>CORS：允许本地 Vite 开发域访问 {@code /api/**}</li>
 *   <li>拦截器：对 {@code /api/**} 挂载 {@link AuthInterceptor}，排除登录注册注销与 actuator</li>
 * </ul>
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;

    public WebCorsConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /** 开发环境前端默认端口；生产需按部署域名调整或使用配置化 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/logout",
                        "/actuator/**"
                );
    }
}
