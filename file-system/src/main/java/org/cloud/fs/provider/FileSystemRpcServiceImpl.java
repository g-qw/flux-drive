package org.cloud.fs.provider;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.cloud.api.dto.*;
import org.cloud.api.service.FileSystemRpcService;
import org.cloud.fs.convert.DirectoryDtoMapper;
import org.cloud.fs.convert.FileDtoMapper;
import org.cloud.fs.dto.DirectoryInput;
import org.cloud.fs.dto.FileInput;
import org.cloud.fs.entity.Directory;
import org.cloud.fs.entity.File;
import org.cloud.fs.service.DirectoryService;
import org.cloud.fs.service.FileService;

import java.util.List;
import java.util.UUID;

@Slf4j
@DubboService
public class FileSystemRpcServiceImpl implements FileSystemRpcService {
    @Override
    public DirectoryDTO initRootDirectory(String uid) {
        try {
            Directory directory = directoryService.createRootDirectory(UUID.fromString(uid));
            log.info("[initRootDirectory] userId={}, directoryId={}", uid, directory.id());
            return DirectoryDtoMapper.INSTANCE.entityToDto(directory);
        } catch (Exception e) {
            log.error("[initRootDirectory] userId={}", uid, e);
            throw e;
        }
    }

    @Override
    public FileDTO addFile(String uid, FileInputDTO dto) {
        try {
            FileInput input = FileDtoMapper.INSTANCE.toInput(dto);
            File file = fileService.createFile(input, UUID.fromString(uid));
            log.info("[addFile] userId={}, bucket={}, storageKey={}, filename={}",
                    uid, file.bucket(), file.storageKey(), file.name());
            return FileDtoMapper.INSTANCE.entityToDto(file);
        }  catch (Exception e) {
            log.error("[addFile] userId={}, file={}", uid, dto.getName(), e);
            throw e;
        }
    }

    @Override
    public DirectoryDTO addDirectory(String uid, DirectoryCreationInputDTO dto) {
        try {
            DirectoryInput input = DirectoryDtoMapper.INSTANCE.toInput(dto);
            Directory directory = directoryService.createDirectory(input, UUID.fromString(uid));
            log.info("[addDirectory] userId={}, directory={}/{}", uid, dto.getParentId(), dto.getName());
            return DirectoryDtoMapper.INSTANCE.entityToDto(directory);
        } catch (Exception e) {
            log.error("[addDirectory] userId={}, directory={}/{}", uid, dto.getParentId(), dto.getName(), e);
            throw e;
        }
    }

    @Override
    public FileDTO getFile(String uid, String fileId) {
        return fileService.getFileRpcViewById(UUID.fromString(fileId), UUID.fromString(uid))
                .map(FileDtoMapper.INSTANCE::viewToDto)
                .orElse(null);
    }

    @Override
    public List<FileDTO> getFiles(String uid, GetFilesRequest request) {
        return fileService.listFileRpcView(
                request.getFileIds().stream().map(UUID::fromString).toList(),
                UUID.fromString(uid)
        ).stream()
        .map(FileDtoMapper.INSTANCE::viewToDto)
        .toList();
    }

    @Override
    public DirectoryDTO getDirectory(String uid, String directoryId) {
        return directoryService.getDirectoryById(UUID.fromString(directoryId), UUID.fromString(uid))
                .map(DirectoryDtoMapper.INSTANCE::entityToDto)
                .orElse(null);
    }

    @Override
    public Long getUsedStorageBytes(String uid) {
        return fileService.getUsedStorageBytes(UUID.fromString(uid));
    }

    @Resource
    private FileService fileService;

    @Resource
    private DirectoryService directoryService;
}
