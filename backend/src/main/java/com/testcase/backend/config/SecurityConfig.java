package com.testcase.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 与安全相关的通用 Bean：提供 BCrypt 密码编码器，供注册、登录校验及
 * {@link com.testcase.backend.service.AuthService} 等注入使用。
 * <p>
 * 本类未启用 Spring Security 过滤器链；接口鉴权由 {@link AuthInterceptor} 完成。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
