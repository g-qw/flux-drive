package org.cloud.fs.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public final class ShareCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /**
     * 生成8位短码：时间戳(6字节) + 随机数(4字节)
     * 冲突概率极低，每秒可生成数百万个不重复码
     */
    public static String generateShortCode() {
        // 当前时间戳（毫秒）取低6字节，约可使用到2089年
        long timestamp = Instant.now().toEpochMilli();
        byte[] timeBytes = new byte[] {
                (byte) (timestamp >> 40),
                (byte) (timestamp >> 32),
                (byte) (timestamp >> 24),
                (byte) (timestamp >> 16),
                (byte) (timestamp >> 8),
                (byte) timestamp
        };

        // 2字节随机数增加熵值
        byte[] randomBytes = new byte[4];
        RANDOM.nextBytes(randomBytes);

        // 合并并编码
        byte[] combined = new byte[10];
        System.arraycopy(timeBytes, 0, combined, 0, 6);
        System.arraycopy(randomBytes, 0, combined, 6, 4);

        return ENCODER.encodeToString(combined);
    }
}
