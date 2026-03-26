package org.cloud.storage.service;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface FileDownloadService {
    /**
     * 下载单文件
     */
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadFile(UUID fileId, UUID userId);

    /**
     * 下载文件分片
     */
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadPart(UUID fileId, UUID userId, String rangeHeader);

    /**
     * zip 下载多个文件
     */
    Mono<ResponseEntity<Flux<DataBuffer>>> downloadFilesAsZipArchive(List<UUID> fileIds, UUID userId);
}
