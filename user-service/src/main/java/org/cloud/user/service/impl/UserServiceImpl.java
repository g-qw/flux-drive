package org.cloud.user.service.impl;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.api.dto.DirectoryDTO;
import org.cloud.api.service.FileSystemRpcService;
import org.cloud.user.config.jwt.JwtProperties;
import org.cloud.user.dto.*;
import org.cloud.user.entity.User;
import org.cloud.user.exception.BusinessException;
import org.cloud.user.repository.UserRepository;
import org.cloud.user.service.EmailService;
import org.cloud.user.service.UserService;
import org.cloud.user.util.jwt.JwtProvider;
import org.cloud.user.util.secure.impl.BcryptPasswordHasher;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {
    @DubboReference(check = false, timeout = 3000, retries = 1, lazy = true)
    private FileSystemRpcService fileSystemRpcService;

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RedissonClient redissonClient;
    private final BcryptPasswordHasher passwordHasher;
    private final EmailService emailService;

    private static final String TOKEN_BLACKLIST_PREFIX = "jwt:blacklist:"; // Token 黑名单前缀
    private static final String USER_TOKEN_VERSION_PREFIX = "jwt:version:"; // 用户Token版本前缀（用于强制下线）

    public UserServiceImpl(UserRepository userRepository,
                           JwtProvider jwtProvider,
                           JwtProperties jwtProperties,
                           RedissonClient redissonClient,
                           BcryptPasswordHasher passwordHasher,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.redissonClient = redissonClient;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
    }

    /**
     * 用户注册
     */
    @Override
    public User register(RegisterRequest request) {
        String email    = request.getEmail();
        String password = request.getPassword();
        String code     = request.getCode();

        // 验证码校验
        emailService.verifyCode(email, code);

        // 查询用户是否存在
        boolean isExists = userRepository.isEmailExists(email);
        if(isExists) {
            throw new BusinessException(409, "该邮箱已注册");
        }

        // 对密码明文进行加密处理
        String encrypted = passwordHasher.hashPassword(password);

        User user = userRepository.createUser(email, encrypted);

        // 创建文件系统的根目录
        UUID rootId;
        try {
            // 正常流程：通过RPC调用文件系统服务创建根目录
            DirectoryDTO root = fileSystemRpcService.initRootDirectory(user.id().toString());
            rootId = UUID.fromString(root.getId());
            userRepository.initRootDir(user.id(), rootId);
        } catch (Exception e) {
            // 补偿机制：RPC失败时，预设根目录ID由文件系统后续自动创建根目录
            rootId = UUID.randomUUID();
            userRepository.initRootDir(user.id(), rootId);
            log.warn("Failed to initialize root directory via RPC for user [{}], using rootDirId [{}] instead. ",
                    user.id(), rootId, e);
        }

        log.info("[register] email={}, root={}", email, rootId);

        return userRepository.getUserById(user.id());
    }

    /**
     * 用户登录，登录成功后设置 token 至 cookie
     */
    @Override
    public String login(LoginRequest request, HttpServletResponse response) {
        String email    = request.getEmail();
        String password = request.getPassword();
        String code     = request.getCode();

        // 验证码校验
        emailService.verifyCode(email, code);

        // 身份验证
        User user = userRepository.getUserByEmail(email);
        boolean valid = passwordHasher.verifyPassword(password, user.pwd());
        if(!valid) {
            throw new BusinessException(400, "登录失败，密码错误");
        }

        // 生成 jwt token
        Map<String, Object> claims = new HashMap<>();
        long tokenVersion = System.currentTimeMillis();
        claims.put("email", user.email());
        claims.put("username", user.username());
        claims.put("version", tokenVersion);

        // 保存当前用户的 Token 版本到 Redis(用于强制下线)
        RBucket<Long> versionBucket = redissonClient.getBucket(USER_TOKEN_VERSION_PREFIX + user.id());
        versionBucket.set(tokenVersion, Duration.ofSeconds(jwtProperties.getExpiration()));

        // 设置 Cookie
        String token = jwtProvider.generateToken(user.id().toString(), claims);
        ResponseCookie cookie = ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(false)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofMillis(jwtProperties.getExpiration()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());


        // 更新登录时间
        Long lastLoginAt = System.currentTimeMillis();
        userRepository.updateLastLoginAt(user.id(), lastLoginAt);

        String maskedToken = token.substring(0, 8) + "***" + token.substring(token.length() - 8);
        log.info("[login] userId={}, email={}, token={}", user.id(), user.email(), maskedToken);

        return token;
    }

    /**
     * 注销当前会话，将访问令牌加入黑名单使其失效
     */
    public void logout(String token) {
        Claims claims = jwtProvider.validate(token);
        Date expiration = claims.getExpiration(); // 过期时间
        String jti = claims.getId(); // jwt token id
        long ttl = expiration.getTime() - System.currentTimeMillis();

        // 将token加入黑名单，TTL设置为token剩余过期时间
        if (ttl > 0) {
            String blacklistKey = TOKEN_BLACKLIST_PREFIX + jti;

            RBucket<String> blacklistBucket = redissonClient.getBucket(blacklistKey);
            blacklistBucket.set("revoked", Duration.ofMillis(ttl));

            String maskedToken = token.substring(0, 8) + "***" + token.substring(token.length() - 8);
            log.info("[logout] userId={}, token={}, jti={}, blacklistTTL={}ms", claims.getSubject(), maskedToken, jti, ttl);
        } else {
            log.warn("Logout ignored - token already expired | user={}, jti={}", claims.getSubject(), jti);
        }
    }

    /**
     * 强制用户下线（修改密码、账号异常等场景）
     * 刷新令牌版本号，使该用户所有已颁发的访问令牌立即失效
     */
    public void revokeAllSessions(UUID userId) {
        RBucket<Long> versionBucket = redissonClient.getBucket(USER_TOKEN_VERSION_PREFIX + userId);
        long newVersion = System.currentTimeMillis();
        long expirationSeconds = jwtProperties.getExpiration();

        versionBucket.set(newVersion, Duration.ofSeconds(expirationSeconds));

        // 优化后的日志：明确操作类型、影响范围、版本号及有效期
        log.info("[revokeAllSessions] userId={}, newTokenVersion={}, versionTTL={}s", userId, newVersion, expirationSeconds);
    }

    /**
     * 查询用户信息
     */
    @Override
    public UserView getUserInfo(UUID id) {
        // 更新用户的已使用存储空间
        try {
            Long usedStorageBytes = fileSystemRpcService.getUsedStorageBytes(String.valueOf(id));
            userRepository.updateUsedCapacity(id, usedStorageBytes);
        } catch (Exception e) {
            log.warn("Failed to fetch used storage for user [{}] from file-system RPC, keeping existing value", id, e);
        }

        return userRepository.getUserView(id);
    }

    /**
     * 重置密码，重置成功后强制下线所有设备
     */
    @Override
    public boolean resetPassword(ResetPwdRequest request, UUID userId) {
        String email = request.getEmail();
        String code = request.getCode();
        String password = request.getNewPassword();

        // 验证码校验
        emailService.verifyCode(email, code);

        // 对密码明文进行加密处理
        String encrypted = passwordHasher.hashPassword(password);

        int affectedRowCount = userRepository.updatePassword(email, encrypted);
        if(affectedRowCount == 1)
            revokeAllSessions(userId);

        log.info("[resetPassword] userId={}, email={}", userId, email);

        return affectedRowCount == 1;
    }

    /**
     * 修改密码，修改成功后强制下线所有设备
     */
    @Override
    public boolean updatePassword(UpdatePwdRequest request, UUID userId) {
        String email = request.getEmail();

        // 身份验证
        User user = userRepository.getUserById(userId);
        if(user == null || !user.email().equals(email)) {
            throw new BusinessException(403, "非法操作");
        }
        boolean valid = passwordHasher.verifyPassword(request.getPassword(), user.pwd());
        if(!valid) {
            throw new BusinessException(400, "登录失败，密码错误");
        }

        // 对密码明文进行加密处理
        String encrypted = passwordHasher.hashPassword(request.getNewPassword());
        int affectedRowCount = userRepository.updatePassword(email, encrypted);
        if(affectedRowCount == 1)
            revokeAllSessions(userId);

        log.info("[updatePassword] userId={}, email={}", userId, user.email());

        return affectedRowCount == 1;
    }

    /**
     * 更新用户信息
     */
    @Override
    public boolean updateUserInfo(UpdateUserInfoRequest request, UUID userId) {
        int affectedRowCount = userRepository.updateUserInfo(request.getUsername(), request.getBio(), request.getAvatar(), userId);
        log.info("[updateUserInfo] userId={}, updated-info={}", userId, request);

        return affectedRowCount == 1;
    }

    /**
     * 检查邮箱是否已注册
     */
    @Override
    public boolean isRegistered(String email) {
        return userRepository.isEmailExists(email);
    }

    /**
     * 搜素用户
     */
    @Override
    public UserSearchDTO searchUser(String key) {
        return new UserSearchDTO(userRepository.getUserIdsByName(key));
    }
}
