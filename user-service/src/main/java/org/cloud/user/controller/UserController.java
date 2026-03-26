package org.cloud.user.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.cloud.user.dto.*;
import org.cloud.user.entity.User;
import org.cloud.user.service.EmailService;
import org.cloud.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "用户 API", description = "用户管理，支持注册、登录、修改用户信息等等")
public class UserController {
    private final UserService userService;
    private final EmailService emailService;

    @PostMapping("/verify")
    @Operation(summary = "邮箱验证", description = "用于注册、登录、修改密码等等的邮箱验证")
    public ApiResponse<Void> verify(@RequestBody @Validated EmailRequest request) {
        emailService.sendVerificationCode(request.getEmail());
        return ApiResponse.success();
    }

    @Operation(summary = "检查邮箱是否已注册", description = "验证邮箱是否已被注册")
    @PostMapping("/check-email")
    public ApiResponse<Boolean> isRegistered(@RequestBody @Validated EmailRequest request) {
        return ApiResponse.success(userService.isRegistered(request.getEmail()));
    }

    @Operation(summary = "用户注册", description = "新用户注册账号")
    @PostMapping("/register")
    public ApiResponse<User> register(@RequestBody @Validated RegisterRequest request) {
        User user = userService.register(request);
        return ApiResponse.success(user);
    }

    @Operation(summary = "用户登录", description = "验证用户账号密码，登录成功后返回访问令牌")
    @PostMapping("/login")
    public ApiResponse<String> login(
            @RequestBody @Validated LoginRequest request,
            HttpServletResponse response) {
        return ApiResponse.success(userService.login(request, response));
    }

    @Operation(summary = "自动登录", description = "若请求中已携带有效的 access_token Cookie，则直接返回该令牌实现自动登录")
    @PostMapping("/auto-login")
    public ApiResponse<String> autoLogin(
            @Parameter(description = "访问令牌Cookie", in = ParameterIn.COOKIE)
            @CookieValue(name = "access_token", required = false) String token) {
        if(token != null) {
            return ApiResponse.success(token);
        }

        return ApiResponse.failure(401, "登录状态已失效，请重新登录");
    }

    @Operation(summary = "用户登出", description = "注销当前会话，将访问令牌加入黑名单使其失效")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Parameter(description = "访问令牌 (Bearer Token)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiIs...")
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization){
        String token = authorization.substring(7); // 去除 "Bearer " 前缀
        userService.logout(token);
        return ApiResponse.success();
    }

    @Operation(summary = "强制登出所有设备", description = "刷新令牌版本号，使该用户所有已颁发的访问令牌立即失效，强制所有设备重新登录")
    @PostMapping("/sessions/revoke-all")
    public ApiResponse<Void> revokeAllSessions(@Parameter(description = "用户 ID") @RequestHeader(value = "UID", required = false) UUID uid) {
        userService.revokeAllSessions(uid);
        return ApiResponse.success();
    }

    @Operation(summary = "获取用户信息", description = "根据用户ID获取用户详细信息")
    @GetMapping("/user-info")
    public ApiResponse<UserView> getUserInfo(@Parameter(description = "用户 ID") @RequestHeader(value = "UID", required = false) UUID uid) {
        UserView userView = userService.getUserInfo(uid);
        return ApiResponse.success(userView);
    }

    @Operation(summary = "重置密码", description = "用户忘记密码时，通过邮箱验证码重置账号密码")
    @PostMapping("/reset-pwd")
    public ApiResponse<Boolean> resetPassword(
            @RequestBody @Validated ResetPwdRequest request,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID", required = false) UUID uid) {
        return ApiResponse.success(userService.resetPassword(request, uid));
    }

    @Operation(summary = "修改密码", description = "已登录用户修改密码，需验证原密码。修改成功后，当前登录态将失效，需重新登录")
    @PostMapping("/update-pwd")
    public ApiResponse<Boolean> updatePassword(
            @RequestBody @Validated UpdatePwdRequest request,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID", required = false) UUID uid) {
        return ApiResponse.success(userService.updatePassword(request, uid));
    }

    @Operation(summary = "更新用户信息", description = "修改用户个人资料信息")
    @PostMapping("/update-info")
    public ApiResponse<Boolean> updateUserInfo(
            @RequestBody @Validated UpdateUserInfoRequest request,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID", required = false) UUID uid) {
        return ApiResponse.success(userService.updateUserInfo(request, uid));
    }

    @Operation(summary = "搜素用户")
    @PostMapping("/search-user")
    public ApiResponse<UserSearchDTO> searchByName(@Parameter(description = "用户名称的关键词") @NotBlank @RequestParam("key") String key) {
        return ApiResponse.success(userService.searchUser(key));
    }
}
