package org.cloud.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.api.dto.FileInputDTO;
import org.cloud.storage.dto.*;
import org.cloud.storage.service.FileUploadService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage/upload")
@RequiredArgsConstructor
@Tag(name = "文件上传 API", description = "支持多文件上传(<100MB)、大文件分片上传(>100MB)")
public class FileUploadController {
    private final FileUploadService fileUploadService;

    @PostMapping(
            value = "/files",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "多文件上传", description = "支持多个中小型文件的上传，根据文件大小自动选择最优上传策略, 文件大小不能超过100MB")
    public Mono<ApiResponse<List<FileUploadResult>>> uploadFiles(
        @Parameter(
                description = "文件列表",
                array = @ArraySchema(
                        schema = @Schema(type = "string", format = "binary", description = "单个文件，最大100MB")
                ),
                explode = Explode.TRUE,
                required = true
        ) @RequestPart("files") Flux<FilePart> files,
        @Parameter(description = "文件大小列表（字节），仅影响上传策略的选择", required = true) @RequestParam List<Long> fileSizes,
        @Parameter(description = "目录ID", required = true) @RequestParam("directoryId") UUID directoryId,
        @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return fileUploadService.uploadFiles(files, fileSizes, directoryId, uid)
                .map(ApiResponse::success)
                .defaultIfEmpty(ApiResponse.failure(400, "未上传文件，请选择要上传的文件"));
    }

    @PostMapping("/multipart/init")
    @Operation(summary = "初始化分片上传", description = "分片上传初始化，返回上传ID")
    public Mono<ApiResponse<String>> initMultipartUpload(
        @Valid @RequestBody InitMultipartUploadRequest request,
        @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid
    ) {
        return fileUploadService.initMultipartUpload(request, uid)
                .map(ApiResponse::success);
    }

    @PostMapping(
            value = "/multipart/part",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(summary = "上传分片", description = "上传分片，分片上传顺序无限制")
    public Mono<ApiResponse<PartUploadResult>> uploadPart(
            @Parameter(description = "上传ID") @RequestParam("uploadId") String uploadId,
            @Parameter(description = "分片编号，从1开始") @RequestParam("partNumber") int partNumber,
            @Parameter(
                    description = "分片二进制数据",
                    schema = @Schema(type = "string", format = "binary")
            ) @RequestPart("part")  Flux<DataBuffer> filePartFlux,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return fileUploadService.uploadPart(uploadId, partNumber, filePartFlux, uid)
                .map(ApiResponse::success);
    }

    @PostMapping("/multipart/complete")
    @Operation(summary = "完成分片上传", description = "合并分片并完成分片上传")
    public Mono<ApiResponse<String>> completeMultipartUpload(
            @Parameter(description = "上传ID") @RequestParam("uploadId") String uploadId,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return fileUploadService.completeMultipartUpload(uploadId, uid)
                .map(FileInputDTO::getMd5)
                .map(ApiResponse::success);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传用户头像", description = "上传用户头像图片，支持 JPG、PNG、GIF 格式，文件大小限制 1MB")
    public Mono<String> uploadAvatar(
            @Parameter(
                    description = "头像文件",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            ) @RequestPart("file") Mono<FilePart> filePart,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return fileUploadService.uploadAvatar(filePart, uid);
    }
}
