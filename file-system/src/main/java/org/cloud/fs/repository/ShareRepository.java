package org.cloud.fs.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.cloud.fs.dto.ShareCreation;
import org.cloud.fs.dto.ShareView;
import org.cloud.fs.entity.ResourceShareDraft;
import org.cloud.fs.entity.ResourceShareTable;
import org.cloud.fs.entity.ResourceShare;
import org.cloud.fs.entity.enums.ResourceType;
import org.cloud.fs.entity.enums.ShareStatus;
import org.cloud.fs.entity.enums.ShareType;
import org.cloud.fs.exception.AccessDeniedException;
import org.cloud.fs.util.BcryptPasswordHasher;
import org.cloud.fs.util.ShareCodeGenerator;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.UUID;

@Repository
public class ShareRepository extends AbstractJavaRepository<ResourceShare, UUID> {
    private final ShareItemRepository shareItemRepository;
    private final ShareAccessRepository accessRepository;
    private final FileRepository fileRepository;
    private final DirectoryRepository directoryRepository;

    private static final ResourceShareTable table = ResourceShareTable.$;

    public ShareRepository(JSqlClient sql,
                           ShareItemRepository shareItemRepository,
                           ShareAccessRepository accessRepository,
                           FileRepository fileRepository,
                           DirectoryRepository directoryRepository) {
        super(sql);
        this.shareItemRepository = shareItemRepository;
        this.accessRepository = accessRepository;
        this.fileRepository = fileRepository;
        this.directoryRepository = directoryRepository;
    }

    public ResourceShare create(ShareCreation dto, UUID userId) {
        // 权限校验
        if(!dto.getFileIds().isEmpty()) {
            if(fileRepository.notOwns(userId, dto.getFileIds())) {
                throw new AccessDeniedException();
            }
        }
        if(!dto.getDirectoryIds().isEmpty()) {
            if(directoryRepository.notOwns(userId, dto.getDirectoryIds())) {
                throw new AccessDeniedException();
            }
        }

        // 创建分享
        ShareType shareType = ShareType.from(dto.getAccessType());

        String passwordHash;
        if(dto.getPassword() != null) {
            passwordHash = BcryptPasswordHasher.hashPassword(dto.getPassword());

        } else {
            passwordHash = null;
        }
        ResourceShare share = ResourceShareDraft.$.produce(
                draft -> {
                    draft.setOwnerId(userId);
                    draft.setCode(ShareCodeGenerator.generateShortCode());
                    draft.setAccessType(shareType.getValue());
                    draft.setExpireAt(dto.getExpireAt());
                    if(passwordHash != null) {
                        draft.setRequirePassword(true);
                        draft.setPasswordHash(passwordHash);
                    } else {
                        draft.setRequirePassword(false);
                    }
                    draft.setStatus(ShareStatus.ACTIVE.getValue());
                    draft.setTitle(dto.getTitle());
                    draft.setDescription(dto.getDescription());
                }
        );

        ResourceShare saved = sql.saveCommand(share).execute().getModifiedEntity();

        // 创建分享资源
        if(CollectionUtils.isEmpty(dto.getFileIds())) {
            shareItemRepository.addBatch(dto.getFileIds(), saved.id(), ResourceType.FILE);
        }

        if(CollectionUtils.isEmpty(dto.getDirectoryIds())) {
            shareItemRepository.addBatch(dto.getDirectoryIds(), saved.id(), ResourceType.DIRECTORY);
        }

        // 设置白名单
        accessRepository.addBatch(dto.getUserIds(), saved.id());

        return saved;
    }

    public ResourceShare getById(UUID id) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .select(table)
                .fetchOne();
    }

    public ResourceShare getByCode(String code) {
        return sql.createQuery(table)
                .where(table.code().eq(code))
                .select(table)
                .fetchOneOrNull();
    }

    public ShareView getShareViewByCode(String code) {
        return sql.createQuery(table)
                .where(table.code().eq(code))
                .select(table.fetch(ShareView.class))
                .fetchOneOrNull();
    }

    // 获取用户拥有的分享
    public List<ShareView> listByOwnerId(UUID ownerId) {
        return sql.createQuery(table)
                .where(table.ownerId().eq(ownerId))
                .select(table.fetch(ShareView.class))
                .execute();
    }
}