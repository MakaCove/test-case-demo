package com.testcase.backend.config;

import com.testcase.backend.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

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
