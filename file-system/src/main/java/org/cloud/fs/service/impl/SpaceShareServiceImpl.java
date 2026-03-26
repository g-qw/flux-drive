package org.cloud.fs.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.ResourceShare;
import org.cloud.fs.entity.enums.ResourceType;
import org.cloud.fs.entity.enums.ShareStatus;
import org.cloud.fs.entity.enums.ShareType;
import org.cloud.fs.exception.SpaceShareException;
import org.cloud.fs.repository.*;
import org.cloud.fs.util.BcryptPasswordHasher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceShareServiceImpl {
    private final ShareRepository shareRepository;
    private final ShareAccessRepository shareAccessRepository;
    private final ShareItemRepository spaceShareItemRepository;
    private final FileRepository fileRepository;
    private final DirectoryRepository directoryRepository;

    public ResourceShare createShare(ShareCreation dto, UUID userId) {
        return shareRepository.create(dto, userId);
    }

    public List<ShareView> getOwnedShares(UUID userId) {
        return shareRepository.listByOwnerId(userId);
    }

    public ShareView getShareViewByCode(String code) {
        ResourceShare share = shareRepository.getByCode(code);

        validateShare(share);

        return shareRepository.getShareViewByCode(code);
    }

    public ShareContent getShareContent(ShareDetailRequest request, UUID userId) {
        ResourceShare share = shareRepository.getById(request.getId());

        validateShare(share);

        if(Objects.equals(share.accessType(), ShareType.PRIVATE.getValue())) { // 白名单校验
            boolean hasAccess = shareAccessRepository.hasAccess(share.id(), userId);
            if(!hasAccess) {
                throw new SpaceShareException("拒绝访问，您不在分享的成员之中");
            }
        }

        // 密码校验
        if(share.requirePassword()) {
            BcryptPasswordHasher.verifyPassword(request.getPassword(), share.passwordHash());
        }

        List<UUID> fileIds = spaceShareItemRepository.listResourceIds(share.id(), ResourceType.FILE);
        List<FileView> files = fileRepository.listFileView(fileIds, share.ownerId());

        return ShareContent.builder()
                .id(share.id())
                .files(files)
                .build();
    }

    public void validateShare(ResourceShare share) {
        // 检查是否过期
        long now = System.currentTimeMillis();
        if(share.expireAt() != null && share.expireAt() < now) {
            throw new SpaceShareException("分享已过期");
        }

        // 检查分享是否未生效或撤销
        if(Objects.equals(share.status(), ShareStatus.ACTIVE.getValue())) {
            throw new SpaceShareException("分享已失效");
        }
    }

}
