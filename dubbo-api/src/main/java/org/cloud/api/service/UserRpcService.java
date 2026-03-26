package org.cloud.api.service;

public interface UserRpcService {
    /**
     * 获取用户的根目录
     */
    String getRootDirectoryId(String userId);

    String getUsernameById(String userId);
}
