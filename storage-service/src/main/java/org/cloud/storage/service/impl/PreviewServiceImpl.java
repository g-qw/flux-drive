package org.cloud.storage.service.impl;

import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.api.dto.FileDTO;
import org.cloud.api.dto.GetFilesRequest;
import org.cloud.api.service.FileSystemRpcService;
import org.cloud.storage.config.minio.MinioProperties;
import org.cloud.storage.dto.MediaCoverDTO;
import org.cloud.storage.dto.PreviewDTO;
import org.cloud.storage.entity.MediaCover;
import org.cloud.storage.repository.MediaCoverRepository;
import org.cloud.storage.service.MinioService;
import org.cloud.storage.service.PreviewService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreviewServiceImpl implements PreviewService {
    @DubboReference(check = false, timeout = 3000, retries = 1, lazy = true)
    private FileSystemRpcService fileSystemRpcService;

    private final MediaCoverRepository mediaCoverRepository;
    private final MinioProperties minioProperties;
    private final MinioService minioService;

    @Override
    public List<MediaCoverDTO> getMediaCovers(List<String> fileHashes, UUID userId) {
        return mediaCoverRepository.listByFileHashes(fileHashes, userId)
                .stream()
                .map(this::buildMediaCoverDTO)
                .toList();
    }

    @Override
    public List<PreviewDTO> getPreviews(List<UUID> fileIds, UUID userId) {
        if(fileIds.isEmpty()) return List.of();

        List<String> ids = fileIds.stream().map(UUID::toString).toList();
        List<FileDTO> files = fileSystemRpcService.getFiles(userId.toString(), new GetFilesRequest(ids));

        return files.parallelStream().map(
            file -> {
                String presignedUrl;
                try {
                    presignedUrl = minioService.getExternalPresignedUrl(file.getBucket(), file.getStorageKey(), Method.GET, minioProperties.getPresignedExpiry());
                } catch (Exception e) {
                    return null;
                }

                return PreviewDTO.builder()
                        .fileId(UUID.fromString(file.getId()))
                        .url(presignedUrl)
                        .build();
            }
        ).toList();
    }

    private MediaCoverDTO buildMediaCoverDTO(MediaCover mediaCover) {
        return MediaCoverDTO.builder()
                .md5(mediaCover.md5())
                .url(minioService.getExternalPresignedUrl(mediaCover.bucket(), mediaCover.storageKey(), Method.GET, minioProperties.getPresignedExpiry()))
                .width(mediaCover.width())
                .height(mediaCover.height())
                .build();
    }
}
