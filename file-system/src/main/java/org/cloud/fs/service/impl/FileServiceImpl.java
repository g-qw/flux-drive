package org.cloud.fs.service.impl;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.babyfish.jimmer.Page;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.enums.FileType;
import org.cloud.fs.entity.File;
import org.cloud.fs.exception.AccessDeniedException;
import org.cloud.fs.exception.InvalidFileNameException;
import org.cloud.fs.repository.DirectoryRepository;
import org.cloud.fs.repository.FileRepository;
import org.cloud.fs.service.FileService;
import org.cloud.message.constant.ExchangeConstants;
import org.cloud.message.constant.RoutingKeyConstants;
import org.cloud.message.dto.FileCleanupMessage;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class FileServiceImpl implements FileService {
    private final DirectoryRepository directoryRepository;
    private final FileRepository fileRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 创建新文件
     *
     * @param fileInput 文件元数据
     * @param userId    当前操作用户 ID
     * @return 创建的文件对象
     */
    @Override
    @Transactional
    public File createFile(FileInput fileInput, UUID userId) throws AccessDeniedException {
        // 权限校验
        if(directoryRepository.notOwns(userId, fileInput.getDirectoryId())) {
            throw new AccessDeniedException();
        }

        // 检查文件名称是否合法
        String name = fileInput.getName().trim();
        fileInput.setName(name);
        if(name.isBlank()) {
            throw new InvalidFileNameException();
        }

        File newFile = fileRepository.createFile(fileInput, userId);

        log.info("[createFile] newFile={}/{}, userId={}", newFile.directoryId(), newFile.name(), userId);

        return newFile;
    }

    /**
     * 重命名文件
     *
     * @param fileId      文件ID
     * @param newName     新文件名
     * @param userId      文件所属用户的ID
     * @return 重命名文件是否成功
     */
    @Override
    @Transactional
    public boolean renameFile(UUID fileId, String newName, UUID userId) {
        newName = newName.trim();
        if(newName.isBlank()) {
            throw new InvalidFileNameException();
        }

        int affectedRowCount = fileRepository.renameFile(fileId, newName, userId);
        log.info("[renameFile] fileId={}, newName={}, userId={}", fileId, newName, userId);

        return affectedRowCount == 1;
    }

    /**
     * 批量删除文件(软删除)
     *
     * @param fileIds   文件ID列表
     * @param userId    文件所属用户的ID
     * @return 成功删除的文件数量
     */
    @Override
    @Transactional
    public int deleteFiles(List<UUID> fileIds, UUID userId) {
        if(fileIds.isEmpty())
            return 0;

        int affectedRowCount = fileRepository.deleteFiles(fileIds, userId);
        log.info("[deleteFiles] fileIds={}, userId={}", fileIds, userId);

        return affectedRowCount;
    }

    /**
     * 批量删除文件（物理删除），文件blob异步删除
     *
     * @param fileIds 文件ID列表
     * @param userId  文件所属用户的 ID
     * @return 成功删除的文件数量
     */
    @Transactional
    public int deleteFilesPhysically(List<UUID> fileIds, UUID userId) {
        if (fileRepository.notOwns(userId, fileIds)) {
            throw new AccessDeniedException();
        }

        List<FileCleanupView> cleanupViews = fileRepository.listFileCleanupViews(fileIds);
        if (cleanupViews.isEmpty()) {
            return 0;
        }

        // 先发消息，记录发送成功的文件ID
        List<UUID> sentFileIds = new ArrayList<>();
        for (FileCleanupView file : cleanupViews) {
            try {
                FileCleanupMessage message = new FileCleanupMessage(
                        file.getId(),
                        file.getBucket(),
                        file.getStorageKey(),
                        System.currentTimeMillis()
                );

                rabbitTemplate.convertAndSend(
                        ExchangeConstants.FILE_EXCHANGE,
                        RoutingKeyConstants.FILE_DELETE_ROUTING_KEY,
                        message,
                        // 添加消息ID用于去重
                        msg -> {
                            msg.getMessageProperties().setMessageId(file.getId().toString());
                            return msg;
                        }
                );

                sentFileIds.add(file.getId());

            } catch (AmqpException e) {
                log.error("Send file delete message failed, fileId={}, skip DB deletion", file.getId(), e);
            }
        }

        int affectedRowCount = fileRepository.deleteFilesPhysically(sentFileIds);
        log.info("[deleteFilesPhysically] sent={}, deleted={}, total={}, userId={}",
                sentFileIds.size(), affectedRowCount, cleanupViews.size(), userId);

        return affectedRowCount;
    }

    /**
     * 批量恢复文件
     *
     * @param fileIds   文件ID
     * @param userId    文件所属用户的ID
     * @return 恢复成功的文件数量
     */
    @Override
    @Transactional
    public int recoverFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId) {
        if(fileIds.isEmpty())
            return 0;

        // 权限校验
        if(targetDirectoryId != null) {
            if(directoryRepository.notOwns(userId, targetDirectoryId)) {
                throw new AccessDeniedException();
            }
        }

        int affectedRowCount = fileRepository.recoverFiles(fileIds, targetDirectoryId, userId);
        log.info("[recoverFiles] fileIds={}, targetDirectoryId={}, userId={}", fileIds, targetDirectoryId, userId);

        return affectedRowCount;
    }

    /**
     * 批量复制文件
     *
     * @param fileIds           文件 ID 列表
     * @param targetDirectoryId 目录 ID
     * @param userId            文件所属用户的ID
     * @return 复制成功的文件数量
     */
    @Override
    @Transactional
    public int copyFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId) {
        if(fileIds.isEmpty())
            return 0;

        // 权限校验
        if(directoryRepository.notOwns(userId, targetDirectoryId)) {
            throw new AccessDeniedException();
        }

        // 文件名称冲突校验
        fileRepository.validateFileNames(fileIds, targetDirectoryId);

        int affectedRowCount = fileRepository.copyFiles(fileIds, targetDirectoryId, userId);
        log.info("[copyFiles] fileIds={}, targetDirectoryId={}, userId={}", fileIds, targetDirectoryId, userId);

        return affectedRowCount;
    }

    /**
     * 批量移动文件
     *
     * @param fileIds           文件ID
     * @param targetDirectoryId 目录ID
     * @param userId            文件所属用户的ID
     * @return 成功移动的文件数量
     */
    @Override
    @Transactional
    public int moveFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId) {
        if(fileIds.isEmpty())
            return 0;

        // 权限校验
        if(directoryRepository.notOwns(userId, targetDirectoryId)) {
            throw new AccessDeniedException();
        }

        // 文件名称冲突校验
        fileRepository.validateFileNames(fileIds, targetDirectoryId);

        int affectedRowCount = fileRepository.moveFiles(fileIds, targetDirectoryId, userId);
        log.info("[moveFiles] fileIds={}, newDirectoryId={}, userId={}", fileIds, targetDirectoryId, userId);

        return affectedRowCount;
    }

    /**
     * 获取文件的普通视图
     *
     * @param fileId 文件 ID
     * @param userId 文件所属用户的的ID
     * @return 文件的用户视图
     */
    @Override
    public Optional<FileView> getFileViewById(UUID fileId, UUID userId) {
        return Optional.ofNullable(fileRepository.getFileViewById(fileId, userId));
    }

    /**
     * 获取文件的RPC视图
     *
     * @param fileId 文件 ID
     * @param userId 文件所属用户的的ID
     * @return 文件的RPC视图
     */
    @Override
    public Optional<FileRpcView> getFileRpcViewById(UUID fileId, UUID userId) {
        return Optional.ofNullable(fileRepository.getFileRpcViewById(fileId, userId));
    }

    /**
     * 批量获取文件的RPC视图
     *
     * @param fileIds 文件 ID 列表
     * @param userId 文件所属用户的 ID
     * @return 文件RPC视图列表
     */
    @Override
    public List<FileRpcView> listFileRpcView(List<UUID> fileIds, UUID userId) {
        return fileRepository.listFileRpcView(fileIds, userId);
    }

    /**
     * 获取用户已使用的存储空间
     *
     * @param userId 用户 ID
     * @return 已使用存储空间大小，单位：字节
     */
    @Override
    public Long getUsedStorageBytes(UUID userId) {
        return fileRepository.sumSizeByUserId(userId);
    }

    /**
     * 分页查询文件
     * @param pageRequest 分页请求
     * @param specification 查询条件
     * @param userId 用户 ID
     * @return 文件分页列表
     */
    public Page<FileView> queryFiles(PageRequest pageRequest, FileSpecification specification, UUID userId) {
        return fileRepository.queryFileViews(pageRequest, specification, userId);
    }

    /**
     * 分页查询软删除的文件
     * @param pageRequest 分页请求
     * @param userId 用户 ID
     * @return 软删除的文件分页列表
     */
    @Override
    public Page<DeletedFileView> queryDeletedFiles(PageRequest pageRequest, UUID userId) {
        return fileRepository.queryDeletedFiles(pageRequest, userId);
    }

    public Page<FileView> queryFilesByType(FileType fileType, PageRequest pageRequest, UUID userId) {
        return fileRepository.queryFileViewsByType(fileType, pageRequest, userId);
    }
}
