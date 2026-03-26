package org.cloud.storage.service;

import org.cloud.api.dto.FileInputDTO;
import org.cloud.storage.dto.FileUploadResult;
import org.cloud.storage.dto.InitMultipartUploadRequest;
import org.cloud.storage.dto.PartUploadResult;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface FileUploadService {
    /**
     * 多文件上传
     */
    Mono<List<FileUploadResult>> uploadFiles(
            Flux<FilePart> files,
            List<Long> fileSizes,
            UUID directoryId,
            UUID userId
    );

    /**
     * 初始化分片上传
     */
    Mono<String> initMultipartUpload(InitMultipartUploadRequest request, UUID userId);

    /**
     * 上传分片
     */
    Mono<PartUploadResult> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> filePart, UUID userId);

    /**
     * 合并分片完成分片上传
     */
    Mono<FileInputDTO> completeMultipartUpload(String uploadId, UUID userId);

    /**
     * 上传头像
     */
    Mono<String> uploadAvatar(Mono<FilePart> filePart, UUID userId);
}
