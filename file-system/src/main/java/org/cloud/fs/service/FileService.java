package org.cloud.fs.service;

import org.babyfish.jimmer.Page;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.enums.FileType;
import org.cloud.fs.entity.File;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileService {

    /**
     * 创建文件
     */
    File createFile(FileInput fileInput, UUID userId);

    /**
     * 重命名文件
     */
    boolean renameFile(UUID fileId, String newName, UUID userId);

    /**
     * 批量删除文件(软删除)
     */
    int deleteFiles(List<UUID> fileIds, UUID userId);

    /**
     * 批量恢复已删除文件
     */
    int recoverFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId);

    /**
     * 批量复制文件到指定目录
     */
    int copyFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId);

    /**
     * 批量移动文件到新目录
     */
    int moveFiles(List<UUID> fileIds, UUID targetDirectoryId, UUID userId);

    /**
     * 获取文件的用户视图
     */
    Optional<FileView> getFileViewById(UUID fileId, UUID userId);

    /**
     * 获取文件的RPC视图
     */
    Optional<FileRpcView> getFileRpcViewById(UUID fileId, UUID userId);

    /**
     * 批量获取文件的RPC视图
     */
    List<FileRpcView> listFileRpcView(List<UUID> fileIds, UUID userId);

    /**
     * 获取用户已使用的存储空间
     */
    Long getUsedStorageBytes(UUID userId);

    /**
     * 搜素文件
     */
    Page<FileView> queryFiles(PageRequest pageRequest, FileSpecification specification, UUID userId);

    /**
     * 分页查询软删除的文件
     */
    Page<DeletedFileView> queryDeletedFiles(PageRequest pageRequest, UUID userId);

    /**
     * 查询指定分类的文件
     */
    Page<FileView> queryFilesByType(FileType fileType, PageRequest pageRequest, UUID userId);
}
