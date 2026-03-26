package org.cloud.user.util.secure.impl;

import org.cloud.user.util.secure.PasswordHasher;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * 使用 bcrypt 算法实现 PasswordHasher 接口。
 */
@Component
public class BcryptPasswordHasher implements PasswordHasher {
    // bcrypt 的工作因子（cost factor），值越高，计算越复杂，安全性越高
    private static final int WORK_FACTOR = 12;

    @Override
    public String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(WORK_FACTOR));
    }

    @Override
    public boolean verifyPassword(String inputPassword, String storedHash) {
        return BCrypt.checkpw(inputPassword, storedHash);
    }
}