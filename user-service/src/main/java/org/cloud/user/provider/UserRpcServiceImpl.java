package org.cloud.user.provider;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.cloud.api.service.UserRpcService;
import org.cloud.user.repository.UserRepository;

import java.util.UUID;

@Slf4j
@DubboService
public class UserRpcServiceImpl implements UserRpcService {

    @Override
    public String getRootDirectoryId(String userId) {
        UUID root = userRepository.getRootDirectoryId(UUID.fromString(userId));
        return root.toString();
    }

    @Override
    public String getUsernameById(String userId) {
       return userRepository.getUsernameById(UUID.fromString(userId));
    }

    @Resource
    private UserRepository userRepository;
}
