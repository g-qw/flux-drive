package org.cloud.fs.util;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

public final class BcryptPasswordHasher {
    // bcrypt 的工作因子（cost factor），值越高，计算越复杂，安全性越高
    private static final int WORK_FACTOR = 12;

    public static String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean verifyPassword(String inputPassword, String storedHash) {
        return BCrypt.checkpw(inputPassword, storedHash);
    }
}