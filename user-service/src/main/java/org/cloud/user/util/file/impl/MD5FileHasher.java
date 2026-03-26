package org.cloud.user.util.file.impl;

import org.cloud.user.util.file.FileHasher;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5FileHasher implements FileHasher {
    private final MessageDigest md;

    public MD5FileHasher() {
        try {
            // 使用枚举类获取算法名称
            this.md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * @param fileBytes 文件的字节数组
     * @return 文件的哈希值
     */
    @Override
    public String hash(byte[] fileBytes) {
        byte[] digest = md.digest(fileBytes);
        return bytesToHex(digest);
    }

    public static void main(String[] args) {
        MD5FileHasher hasher = new MD5FileHasher();
        String testString = "Hello, World!";
        byte[] testBytes = testString.getBytes();
        String hashResult = hasher.hash(testBytes);
        System.out.println("MD5 hash of \"" + testString + "\": " + hashResult);
    }
}
