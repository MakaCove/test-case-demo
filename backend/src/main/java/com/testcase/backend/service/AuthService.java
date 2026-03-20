package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.testcase.backend.common.UnauthorizedException;
import com.testcase.backend.dto.AuthDtos;
import com.testcase.backend.entity.UserEntity;
import com.testcase.backend.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Map<String, Long> tokenUserMap = new ConcurrentHashMap<>();
    private final String bootstrapAdminPassword;

    public AuthService(
            UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${app.auth.bootstrap-admin-password:admin123}") String bootstrapAdminPassword
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    public AuthDtos.LoginResponse login(String username, String rawPassword) {
        UserEntity user = findActiveUserByUsername(username);
        if (user == null) {
            log.warn("login failed: username not found, username={}", username);
            throw new IllegalArgumentException("invalid username or password");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("login failed: password mismatch, username={}", username);
            throw new IllegalArgumentException("invalid username or password");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenUserMap.put(token, user.getId());
        log.info("login success, userId={}, username={}", user.getId(), user.getUsername());
        return new AuthDtos.LoginResponse(token, toUserInfo(user));
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Long removed = tokenUserMap.remove(token);
        if (removed != null) {
            log.info("logout success, userId={}", removed);
        }
    }

    public AuthDtos.UserInfo currentUser(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("missing token");
        }
        Long userId = tokenUserMap.get(token);
        if (userId == null) {
            throw new UnauthorizedException("invalid token");
        }
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getId, userId)
                        .eq(UserEntity::getIsDeleted, 0)
                        .eq(UserEntity::getStatus, "ACTIVE")
                        .last("LIMIT 1")
        );
        if (user == null) {
            tokenUserMap.remove(token);
            throw new UnauthorizedException("invalid token");
        }
        return toUserInfo(user);
    }

    public void ensureAdminPasswordSecurity() {
        UserEntity admin = findActiveUserByUsername("admin");
        if (admin == null) {
            log.warn("admin user not found, skip password bootstrap");
            return;
        }
        String currentHash = admin.getPasswordHash() == null ? "" : admin.getPasswordHash();
        if (currentHash.startsWith("$2a$") || currentHash.startsWith("$2b$") || currentHash.startsWith("$2y$")) {
            log.info("admin password already uses bcrypt");
            return;
        }

        String newHash = passwordEncoder.encode(bootstrapAdminPassword);
        int updated = userMapper.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .set(UserEntity::getPasswordHash, newHash)
                        .eq(UserEntity::getId, admin.getId())
                        .eq(UserEntity::getIsDeleted, 0)
        );
        if (updated > 0) {
            log.warn("admin password upgraded to bcrypt hash");
        }
    }

    private UserEntity findActiveUserByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, username)
                        .eq(UserEntity::getIsDeleted, 0)
                        .eq(UserEntity::getStatus, "ACTIVE")
                        .last("LIMIT 1")
        );
    }

    private AuthDtos.UserInfo toUserInfo(UserEntity user) {
        String displayName = (user.getDisplayName() == null || user.getDisplayName().isBlank())
                ? user.getUsername()
                : user.getDisplayName();
        return new AuthDtos.UserInfo(user.getId(), user.getUsername(), displayName);
    }
}
