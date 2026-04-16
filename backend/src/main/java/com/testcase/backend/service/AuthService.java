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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务：内存 {@code token -> userId} 会话（非 JWT）、BCrypt 密码、注册登录改密；
 * {@link #ensureAdminPasswordSecurity()} 在启动时将非 bcrypt 的 admin 密码迁移为配置项哈希。
 */
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
        String normalizedUsername = normalizeUsername(username);
        UserEntity user = findActiveUserByUsername(normalizedUsername);
        if (user == null) {
            log.warn("login failed: username not found, username={}", normalizedUsername);
            throw new IllegalArgumentException("invalid username or password");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("login failed: password mismatch, username={}", normalizedUsername);
            throw new IllegalArgumentException("invalid username or password");
        }

        String token = issueToken(user.getId());
        log.info("login success, userId={}, username={}", user.getId(), user.getUsername());
        return new AuthDtos.LoginResponse(token, toUserInfo(user));
    }

    public AuthDtos.LoginResponse register(String username, String displayName, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        validateUsername(normalizedUsername);
        validatePassword(rawPassword);
        if (findAnyUserByUsername(normalizedUsername) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedUsername);
        UserEntity entity = new UserEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUsername(normalizedUsername);
        entity.setDisplayName(normalizedDisplayName);
        entity.setPasswordHash(passwordEncoder.encode(rawPassword));
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setIsDeleted(0);
        userMapper.insert(entity);

        String token = issueToken(entity.getId());
        log.info("register success, userId={}, username={}", entity.getId(), entity.getUsername());
        return new AuthDtos.LoginResponse(token, toUserInfo(entity));
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

    public void changePassword(String token, String oldPassword, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("missing token");
        }
        validatePassword(newPassword);
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
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }
        int updated = userMapper.update(
                null,
                new LambdaUpdateWrapper<UserEntity>()
                        .set(UserEntity::getPasswordHash, passwordEncoder.encode(newPassword))
                        .set(UserEntity::getUpdatedAt, LocalDateTime.now())
                        .eq(UserEntity::getId, user.getId())
                        .eq(UserEntity::getIsDeleted, 0)
        );
        if (updated == 0) {
            throw new IllegalArgumentException("密码修改失败");
        }

        // 密码更新后，当前用户所有旧 token 失效，要求重新登录。
        tokenUserMap.entrySet().removeIf(entry -> user.getId().equals(entry.getValue()));
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

    private UserEntity findAnyUserByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getUsername, username)
                        .eq(UserEntity::getIsDeleted, 0)
                        .last("LIMIT 1")
        );
    }

    private String issueToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenUserMap.put(token, userId);
        return token;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeDisplayName(String displayName, String fallbackUsername) {
        if (displayName == null || displayName.isBlank()) {
            return fallbackUsername;
        }
        return displayName.trim();
    }

    private void validateUsername(String username) {
        if (username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度需为 3-32 位");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new IllegalArgumentException("密码长度需为 6-64 位");
        }
    }

    private AuthDtos.UserInfo toUserInfo(UserEntity user) {
        String displayName = (user.getDisplayName() == null || user.getDisplayName().isBlank())
                ? user.getUsername()
                : user.getDisplayName();
        return new AuthDtos.UserInfo(user.getId(), user.getUsername(), displayName);
    }
}
