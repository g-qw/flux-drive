package org.cloud.user.service;

import jakarta.servlet.http.HttpServletResponse;
import org.cloud.user.dto.*;
import org.cloud.user.entity.User;

import java.util.UUID;

public interface UserService {
    User register(RegisterRequest request);

    String login(LoginRequest request, HttpServletResponse response);

    void logout(String token);

    void revokeAllSessions(UUID userId);

    UserView getUserInfo(UUID userId);

    boolean resetPassword(ResetPwdRequest request, UUID userId);

    boolean updatePassword(UpdatePwdRequest request, UUID userId);

    boolean updateUserInfo(UpdateUserInfoRequest request, UUID userId);

    boolean isRegistered(String email);

    UserSearchDTO searchUser(String key);
}
