package com.testcase.backend.config;

import com.testcase.backend.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后执行一次：确保内置管理员账号密码符合安全策略（如弱密码升级、哈希迁移等），
 * 具体逻辑见 {@link AuthService#ensureAdminPasswordSecurity()}。
 */
@Component
public class AdminPasswordBootstrapRunner implements ApplicationRunner {
    private final AuthService authService;

    public AdminPasswordBootstrapRunner(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.ensureAdminPasswordSecurity();
    }
}
