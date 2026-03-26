package org.cloud.user.util.secure;

import java.security.SecureRandom;

/**
 * PasswordHasher 接口定义了密码哈希和验证的基本功能。
 * 它提供了一个默认的生成盐值（salt）的方法，以及密码哈希和验证的方法。
 * 实现该接口的类需要提供具体的哈希算法实现。
 */
public interface PasswordHasher {
    /**
     * 对密码进行哈希处理
     * @param password 原始密码
     * @return 哈希后的密码字符串
     */
    String hashPassword(String password);

    /**
     * 验证密码是否正确
     * @param inputPassword 用户输入的密码
     * @param storedHash 存储的哈希值
     * @return 验证结果
     */
    boolean verifyPassword(String inputPassword, String storedHash);
}