package org.cloud.api.service;

import org.cloud.api.dto.*;

import java.util.List;

public interface FileSystemRpcService {
    /**
     * 创建根目录
     *
     * @param uid 用户 id
     * @return 用户文件系统的根目录 DTO
     */
    DirectoryDTO initRootDirectory(String uid);

    /**
     * 创建文件
     *
     * @param uid 用户 id
     * @param dto 文件的元数据
     * @return 已创建的文件 DTO
     */
    FileDTO addFile(String uid, FileInputDTO dto);

    /**
     * 创建目录
     *
     * @param uid 用户 id
     * @param dto 目录的元数据输入
     * @return 创建的目录 DTO
     */
    DirectoryDTO addDirectory(String uid, DirectoryCreationInputDTO dto);

    /**
     * 获取文件
     *
     * @param uid 用户 id
     * @param fileId 文件 id
     * @return 文件 DTO
     */
    FileDTO getFile(String uid, String fileId);

    /**
     * 获取文件列表
     * @param uid 用户 id
     * @param request 文件 id 列表
     * @return 文件 DTO 列表
     */
    List<FileDTO> getFiles(String uid, GetFilesRequest request);

    /**
     * 获取目录
     *
     * @param uid 用户 id
     * @param directoryId 目录 id
     * @return 目录 DTO
     */
    DirectoryDTO getDirectory(String uid, String directoryId);

    /**
     * 获取用户已使用的存储空间
     * @param uid 用户 id
     * @return 已使用的存储空间大小，单位：字节
     */
    Long getUsedStorageBytes(String uid);
}
