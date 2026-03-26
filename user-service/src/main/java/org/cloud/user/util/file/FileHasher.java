package org.cloud.user.util.file;

import java.io.InputStream;

public interface FileHasher {
    /**
     * 对文件的字节数组求哈希值
     * @param fileBytes 文件的字节数组
     * @return 文件的哈希值
     */
    String hash(byte[] fileBytes);
}
