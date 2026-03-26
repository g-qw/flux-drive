package org.cloud.storage.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class StorageKeyGenerator {
    private static final MessageDigest MD;
    private static final int PREFIX_BYTES = 1; // 2位十六进制 = 1字节, 16^2 = 256 个前缀桶

    static {
        try {
            MD = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError("SHA-256 algorithm not available: " + e.getMessage());
        }
    }

    public static String generateKey(UUID userId) {
        String hashPrefix = computeHashPrefix(userId);
        UUID fileKey = UUID.randomUUID();

        return String.format("%s/%s/%s", hashPrefix, userId, fileKey);
    }

    public static String generateKey(UUID userId, UUID fileId) {
        String hashPrefix = computeHashPrefix(userId);
        return String.format("%s/%s/%s", hashPrefix, userId, fileId);
    }

    /**
     * 计算哈希前缀，用于分散存储
     * 将 userId 的字符串表示进行 SHA-256 哈希，取前2字节作为4位十六进制前缀
     */
    private static String computeHashPrefix(UUID input) {
        byte[] hash = MD.digest(input.toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash, 0, PREFIX_BYTES);
    }
}
