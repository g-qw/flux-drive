package org.cloud.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloud.storage.service.FileDownloadService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage/download")
@RequiredArgsConstructor
@Tag(name = "文件下载 API", description = "支持单文件下载、分片下载、批量打包zip下载")
public class FileDownloadController {
    private final FileDownloadService fileDownloadService;

    @GetMapping("/{fileId}")
    @Operation(summary = "单文件流式下载", description = "支持大文件的流式传输，文件内容不会一次性加载到内存")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFile(
            @Parameter(description = "文件ID") @PathVariable UUID fileId,
            @Parameter(description = "用户 ID", required = true) @RequestHeader(value = "UID") UUID uid
    ) {
        return fileDownloadService.downloadFile(fileId, uid);
    }

    @GetMapping("/{fileId}/part")
    @Operation(summary = "文件分片下载（支持断点续传）", description = "支持 HTTP Range 请求，实现断点续传和随机访问")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadPart(
            @Parameter(description = "文件ID") @PathVariable UUID fileId,
            @Parameter(description = "用户 ID", required = true) @RequestHeader(value = "UID") UUID uid,
            @Parameter(
                    description = "分片的Range, 标准格式为 bytes=start-end, 不提供则为整个文件",
                    examples = {
                            @ExampleObject(
                                    name = "下载前1MB",
                                    description = "从文件开头下载前1MB数据（字节范围 0-1048575）",
                                    value = "bytes=0-1048575"
                            ),
                            @ExampleObject(
                                    name = "从1MB位置下载至末尾",
                                    description = "从第1MB字节位置开始下载到文件末尾（断点续传场景）",
                                    value = "bytes=1048576-"
                            ),
                            @ExampleObject(
                                    name = "下载最后1MB",
                                    description = "仅下载文件最后1MB数据（常用于获取文件尾部元信息）",
                                    value = "bytes=-1048576"
                            ),
                    }
            ) @RequestHeader(value = "Range", required = false) String range
    ) {
        return fileDownloadService.downloadPart(fileId, uid, range);
    }

    @GetMapping("/zip")
    @Operation(summary = "多文件打包ZIP下载", description = "将多个文件打包成 ZIP 格式流式下载, 文件数量建议不超过 100, zip过大会拒绝，文件名为 download_(hash).zip")
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFilesAsZip(
            @Parameter(description = "文件ID列表") @RequestParam List<UUID> fileIds,
            @Parameter(description = "用户 ID", required = true) @RequestHeader(value = "UID") UUID uid
    ) {
        return fileDownloadService.downloadFilesAsZipArchive(fileIds, uid);
    }
}
