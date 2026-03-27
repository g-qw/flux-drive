package org.cloud.fs.service.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.babyfish.jimmer.Page;
import org.cloud.api.service.UserRpcService;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.Directory;
import org.cloud.fs.exception.*;
import org.cloud.fs.repository.DirectoryRepository;
import org.cloud.fs.repository.FileRepository;
import org.cloud.fs.service.DirectoryService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class DirectoryServiceImpl implements DirectoryService {
    @DubboReference(check = false, timeout = 3000, retries = 1, lazy = true)
    private UserRpcService userRpcService;

    private final DirectoryRepository directoryRepository;
    private final FileRepository fileRepository;
    private final RedissonClient redissonClient;

    private static final long DIRECTORY_SIZE_CACHE_HOURS = 1;

    /**
     * 创建根目录
     *
     * @param userId 用户ID
     * @return 根目录
     */
    @Override
    @Transactional
    public Directory createRootDirectory(UUID userId) {
        Directory root = directoryRepository.getRootDirectory(userId);

        if (root == null) {
            root = directoryRepository.createRootDirectory(UUID.randomUUID(), userId);

            log.debug("[createRootDirectory] userId={}, root={}", userId, root.id());
        }

        return root;
    }

    /**
     * @param userId 用户ID
     * @return 根目录
     */
    @Override
    public Optional<Directory> getRootDirectory(UUID userId) {
        Directory root = directoryRepository.getRootDirectory(userId);

        // 若根目录未创建，则使用预设ID自动创建
        if (root == null) {
            String rootId = userRpcService.getRootDirectoryId(userId.toString()); // 获取预设的根目录ID
            root = directoryRepository.createRootDirectory(UUID.fromString(rootId), userId);
        }

        return Optional.ofNullable(root);
    }

    /**
     * @param directoryId 目录ID
     * @param userId      当前操作用户ID
     * @return 目录对象
     */
    @Override
    public Optional<Directory> getDirectoryById(UUID directoryId, UUID userId) throws AccessDeniedException {
        return Optional.ofNullable(directoryRepository.getDirectoryById(directoryId, userId));
    }

    /**
     * @param directoryId 目录ID
     * @param userId      当前操作用户ID
     * @return 目录视图
     */
    @Override
    public Optional<DirectoryView> listDirectoryContent(UUID directoryId, UUID userId) throws AccessDeniedException {
        Long updatedAt = directoryRepository.getUpdatedAt(directoryId);

        // 懒惰计算目录大小
        if(updatedAt != null) {
            // 如果距离上次更新超过 1 小时
            if (Instant.ofEpochMilli(updatedAt).plus(DIRECTORY_SIZE_CACHE_HOURS, ChronoUnit.HOURS).isBefore(Instant.now())) {
                long totalFileSize = fileRepository.sumSizeByDirectoryId(directoryId);
                long totalDirSize = directoryRepository.sumSizeByDirectoryId(directoryId);
                long totalSize = totalFileSize + totalDirSize;

                directoryRepository.updateDirectorySize(directoryId, totalSize);
            }
        }

        return Optional.ofNullable(directoryRepository.getDirectoryView(directoryId, userId));
    }

    /**
     * 创建新目录
     *
     * @param input  创建目录的输入数据
     * @param userId 当前操作的用户 ID
     * @throws AccessDeniedException  用户没有权限创建目录
     * @throws DuplicateDirectoryNameException 目录名称重复
     * @throws InvalidDirectoryNameException 目录名称不合法
     * @return 创建的新目录
     */
    @Override
    @Transactional
    public Directory createDirectory(DirectoryInput input, UUID userId)
            throws AccessDeniedException, DuplicateDirectoryNameException {
        UUID parentId = input.getParentId();
        String name = input.getName().trim();

        // 权限校验
        if(directoryRepository.notOwns(userId, parentId)) {
            throw new AccessDeniedException();
        }

        // 检查目录名称是否合法
        if(name.isEmpty()) {
            throw new InvalidDirectoryNameException(false, null);
        }
        boolean lengthExceeded = name.length() > 1024;
        String illegalChars = name.contains("/") ? "/" : null;
        if(lengthExceeded || illegalChars != null) {
            throw new InvalidDirectoryNameException(lengthExceeded, illegalChars);
        }

        // 检查是否存在名称重复的目录
        if(directoryRepository.isDirectoryExist(parentId, name)) {
            throw new DuplicateDirectoryNameException(name);
        }

        Directory newDir = directoryRepository.createDirectory(userId, input.getParentId(), name);
        log.debug("[createDirectory] parentId={}, name={}, userId={}", newDir.parentId(), newDir.name(), userId);

        return newDir;
    }

    /**
     * 重命名目录
     *
     * @param directoryId  目录ID
     * @param newName      新名称
     * @param userId       当前操作用户ID
     * @return 重命名是否成功
     */
    @Override
    @Transactional
    public boolean renameDirectory(UUID directoryId, String newName, UUID userId) throws AccessDeniedException {
        // 检查目录名称是否合法
        if(newName.isEmpty()) {
            throw new InvalidDirectoryNameException(false, null);
        }
        boolean lengthExceeded = newName.length() > 1024;
        String illegalChars = newName.contains("/") ? "/" : null;
        if(lengthExceeded || illegalChars != null) {
            throw new InvalidDirectoryNameException(lengthExceeded, illegalChars);
        }

        int affectedRowCount = directoryRepository.renameDirectory(directoryId, newName, userId);
        log.debug("[renameDirectory] directoryId={}, newName={}, userId={}", directoryId, newName, userId);

        return affectedRowCount == 1;
    }

    /**
     * 更新目录大小
     * @param directoryId 目录 ID
     * @param userId 当前操作用户 ID
     * @return 目录的最新大小(字节)
     */
    @Override
    public long updateDirectorySize(UUID directoryId, UUID userId) {
        // 权限校验
        if(directoryRepository.notOwns(userId, directoryId)) {
            throw new AccessDeniedException();
        }

        Long totalFileSize = fileRepository.sumSizeByDirectoryId(directoryId);
        Long totalDirSize = directoryRepository.sumSizeByDirectoryId(directoryId);
        long totalSize = totalFileSize + totalDirSize;

        directoryRepository.updateDirectorySize(directoryId, totalSize);

        return totalSize;
    }


    /**
     * 批量删除目录(软删除)
     * Jimmer 会自动处理：级联删除所有子目录（递归）级联删除所有文件（包括子目录下的文件）
     *
     * @param directoryIds  被删除的目录的ID列表
     * @param userId        目录所属用户的 ID
     * @return 删除成功的目录数量
     */
    @Override
    @Transactional
    public int deleteDirectories(List<UUID> directoryIds, UUID userId) {
        if(directoryIds.isEmpty())
            return 0;

        // 权限校验
        if(directoryRepository.notOwns(userId, directoryIds)) {
            throw new AccessDeniedException();
        }

        // 删除目录下的所有文件
        int deletedFilesCount = fileRepository.deleteFilesByDirectoryIds(directoryIds);

        // 删除所有后代目录
        List<UUID> subDirectoryIds = directoryRepository.listDescendantIds(directoryIds);
        if(!subDirectoryIds.isEmpty()) {
            directoryRepository.deleteDirectories(subDirectoryIds);

            // 删除所有后代目录的文件
            fileRepository.deleteFilesByDirectoryIds(subDirectoryIds);
        }

        // 删除目录
        int affectedRowCount = directoryRepository.deleteDirectories(directoryIds);

        log.debug("[deleteDirectories] directoryIds={}, userId={}", directoryIds, userId);

        return affectedRowCount;
    }

    /**
     * 批量删除目录（物理删除）
     * 非递归处理，仅用于清理软删除后过期的目录
     *
     * @param directoryIds  被删除的目录的ID列表
     * @param userId        目录所属用户的 ID
     * @return 删除成功的目录数量
     */
    public int deleteDirectoriesPhysically(List<UUID> directoryIds, UUID userId) {
        // 权限校验
        if(directoryRepository.notOwns(userId, directoryIds)) {
            throw new AccessDeniedException();
        }

        return directoryRepository.deleteDirectoriesPhysically(directoryIds);
    }

    /**
     * 批量恢复目录
     *
     * @param directoryIds      要恢复的目录ID列表
     * @param userId            目录所属用户的 ID
     * @return 恢复成功的目录数量
     */
    @Override
    @Transactional
    public int recoverDirectories(List<UUID> directoryIds, UUID userId) {
        if(directoryIds.isEmpty())
            return 0;

        // 权限校验
        if(directoryRepository.notOwns(userId, directoryIds)) {
            throw new AccessDeniedException();
        }

        // 恢复目录下的所有文件
        fileRepository.recoverFilesByDirectoryIds(directoryIds);

        // 恢复所有后代目录
        List<UUID> subDirectoryIds = directoryRepository.listDescendantIds(directoryIds);
        directoryRepository.recoverDirectories(subDirectoryIds);

        // 恢复所有后代目录的文件
        fileRepository.recoverFilesByDirectoryIds(subDirectoryIds);

        // 恢复目录列表
        int affectedRowCount = directoryRepository.recoverDirectories(directoryIds);
        log.debug("[recoverDirectories] directoryIds={},  userId={}", directoryIds, userId);

        return affectedRowCount;
    }

    /**
     * 批量移动目录
     *
     * @param directoryIds 被移动的目录的ID列表
     * @param newParentId  新的父目录
     * @param userId       当前操作的用户 ID
     * @throws AccessDeniedException 当用户没有权限移动目录时抛出
     * @return 移动成功的目录数量
     */
    @Override
    @Transactional
    public int moveDirectories(List<UUID> directoryIds, UUID newParentId, UUID userId) throws AccessDeniedException {
        // 权限校验
        if(directoryRepository.notOwns(userId, newParentId) || directoryRepository.notOwns(userId, directoryIds)) {
            throw new AccessDeniedException();
        }

        // 检查父目录是否为当前目录的后代
        List<UUID> subDirectoryIds = directoryRepository.listDescendantIds(directoryIds);
        if(subDirectoryIds.contains(newParentId)) {
            throw new DirectoryCircularDependencyException();
        }

        // 目录名称冲突校验
        directoryRepository.validateDirectoryNames(directoryIds, newParentId);

        return directoryRepository.moveDirectories(directoryIds, newParentId, userId);
    }

    /**
     * 解析路径
     *
     * @param path 目标路径，以 "/" 为分隔符，必须以 "/" 开头
     * @param userId 用户 ID
     * @throws ResourceNotFoundException 当路径不存在时抛出
     * @return 从根目录到路径的完整目录列表（包含根目录）
     */
    @Override
    public List<Directory> parsePath(String path, UUID userId) {
        List<Directory> chain = new ArrayList<>();

        // 查询根目录
        Directory root = directoryRepository.getRootDirectory(userId);
        if(root == null) {
            throw new ResourceNotFoundException("/");
        }
        chain.add(root);

        if("/".equals(path) || path == null || path.isBlank()) {
            return chain;
        }

        // 按 / 分隔路径
        String[] segments = Arrays.stream(path.split("/"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
        StringBuilder fullPath = new StringBuilder();
        Directory currentDir = root;
        for(String segment: segments) {
            if(segment == null || segment.isBlank()) {
                throw new ResourceNotFoundException(fullPath.toString());
            }

            fullPath.append("/").append(segment);

            // 查询下一级目录
            Directory next = directoryRepository.getDirectoryByName(currentDir.id(), segment);
            if(next == null) {
                throw new ResourceNotFoundException(fullPath.toString());
            }

            chain.add(next);
            currentDir = next;
        }

        return chain;
    }

    /**
     * 获取目录的路径节点链
     * @param directoryId 目录 ID
     * @param userId 用户 ID
     * @return 目录的路径节点链
     */
    public List<DirectoryInfoView> getDirectoryPathChain(UUID directoryId, UUID userId) {
        String cacheKey = String.format("path:%s:ids", directoryId);
        RBucket<List<UUID>> bucket = redissonClient.getBucket(cacheKey);
        List<UUID> cachedIds = bucket.get();
        if (cachedIds != null) {
            return directoryRepository.getDirectoryPathChain(cachedIds);
        }

        List<DirectoryInfoView> chain = directoryRepository.getDirectoryPathChain(directoryId, userId);

        // 提取ID列表并缓存
        List<UUID> ids = chain.stream()
                .map(DirectoryInfoView::getId)
                .toList();
        bucket.set(ids, Duration.ofHours(6));

        return chain;
    }

    /**
     * 获取目录的绝对路径
     * @param directoryId 目录 ID
     * @param userId 用户 ID
     * @return 目录的绝对路径
     */
    public String getDirectoryPath(UUID directoryId, UUID userId) {
        // 查询缓存
        String cacheKey = String.format("path:%s:str", directoryId);
        RBucket<String> bucket = redissonClient.getBucket(cacheKey);
        String cachedPath = bucket.get();
        if (cachedPath != null) {
            return cachedPath;
        }

        // 缓存未命中
        String path = directoryRepository.getDirectoryPath(directoryId, userId);

        // 写入缓存，有效期30分钟
        bucket.set(path, Duration.ofMinutes(30));

        return path;
    }

    /**
     * 获取目录节点
     *
     * @param userId 用户 ID
     * @return 目录树对象
     */
    @Override
    public DirectoryNode getDirectoryNode(UUID directoryId, UUID userId) {
        // 查询目录
        Directory directory = directoryRepository.getDirectoryById(directoryId, userId);
        if(directory == null) {
            throw new AccessDeniedException();
        }

        // 查询所有子目录节点
        List<DirectoryNodeView> children = directoryRepository.listDirectoryNodeView(directoryId, userId);

        return DirectoryNode.builder()
                .id(directoryId)
                .name(directory.name())
                .children(children)
                .build();
    }

    /**
     * 目录分页查询
     * @param pageRequest 分页请求
     * @param specification 查询条件
     * @param userId 用户 ID
     * @return 分页结果
     */
    public Page<Directory> queryDirectories(PageRequest pageRequest, DirectorySpecification specification, UUID userId) {
        return directoryRepository.queryDirectories(pageRequest, specification, userId);
    }

    /**
     * 分页查询软删除的目录
     * @param pageRequest 分页请求
     * @param userId 用户 ID
     * @return 分页结果
     */
    @Override
    public Page<DeletedDirectoryView> queryDeletedDirectories(PageRequest pageRequest, UUID userId) {
        return directoryRepository.queryDeletedDirectories(pageRequest, userId);
    }
}
