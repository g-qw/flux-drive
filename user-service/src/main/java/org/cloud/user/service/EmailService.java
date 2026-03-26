package org.cloud.user.service;

public interface EmailService {
    void sendVerificationCode(String email);

    void verifyCode(String email, String code);
}
