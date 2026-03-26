package org.cloud.storage.service.impl;

import com.google.common.hash.Hashing;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.api.dto.FileDTO;
import org.cloud.api.dto.GetFilesRequest;
import org.cloud.api.service.FileSystemRpcService;
import org.cloud.storage.config.transfer.FileTransferConfig;
import org.cloud.storage.exception.BatchZipDownloadException;
import org.cloud.storage.exception.FileRangeDownloadException;
import org.cloud.storage.service.FileDownloadService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class FileDownloadServiceImpl implements FileDownloadService {
    @DubboReference(check = false, timeout = 3000, retries = 1, lazy = true)
    private FileSystemRpcService fileSystemRpcService;

    private final FileTransferConfig transferConfig;
    private final MinioClient minioClient;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TempFileCache tempFileCache;

    private static Path TEMP_DOWNLOAD_DIR = Paths.get("tmp");
    public static final Set<String> COMPRESSED_EXTENSIONS = Set.of(
            // 压缩格式
            "zip", "gz", "bz2", "xz", "7z", "rar", "tar", "tgz", "tbz",
            // 图片（已压缩）
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico",
            // 视频/音频（已压缩）
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm",
            "mp3", "aac", "ogg", "wma", "flac", "m4a",
            // 文档（通常已压缩）
            "pdf", "docx", "xlsx", "pptx", "odt", "ods", "odp",
            // 其他二进制
            "exe", "dll", "so", "dmg", "pkg", "deb", "rpm"
    );

    static {
        try {
            Files.createDirectories(TEMP_DOWNLOAD_DIR);
        } catch (IOException e) {
            // 创建失败，使用系统默认临时目录
            TEMP_DOWNLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"));
        }
    }

    public FileDownloadServiceImpl(
            FileTransferConfig transferConfig,
            MinioClient minioClient,
            @Qualifier("sharedTaskExecutor") ThreadPoolTaskExecutor taskExecutor, TempFileCache tempFileCache) {
        this.transferConfig = transferConfig;
        this.minioClient = minioClient;
        this.taskExecutor = taskExecutor;
        this.tempFileCache = tempFileCache;
    }

    /**
     * 下载单文件
     * @param fileId 文件 ID
     * @param userId 用户 ID
     * @return (文件资源, http响应头)
     */
    @Override
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFile(UUID fileId, UUID userId) {
        FileTransferConfig.DownloadConfig cfg = transferConfig.getDownloadConfig();

        return Mono.fromCallable(() -> fileSystemRpcService.getFile(userId.toString(), fileId.toString()))
            .subscribeOn(Schedulers.fromExecutor(taskExecutor))
            .switchIfEmpty(Mono.error(new FileRangeDownloadException("文件不存在或已被删除")))
            .map(file -> {
                long startTime = System.currentTimeMillis();

                Flux<DataBuffer> flux = DataBufferUtils.readInputStream(
                        () -> minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(file.getBucket())
                                        .object(file.getStorageKey())
                                        .build()
                        ),
                        new DefaultDataBufferFactory(),
                        cfg.getStreamBufferSizeBytes()
                ).doFinally(signal ->
                        log.info("[downloadFile] fileId={}, filename={}, size={} bytes, elapsed={} ms",
                                fileId, file.getName(), file.getSize(), System.currentTimeMillis() - startTime)
                );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(file.getMimeType()));
            headers.setContentDisposition(ContentDisposition.attachment().filename(file.getName(), StandardCharsets.UTF_8).build()); // 弹出下载
            headers.setContentLength(file.getSize());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(flux);
        });
    }

    /**
     * 下载文件分片
     * @param fileId 文件 ID
     * @param userId 用户 ID
     * @param rangeHeader Range 请求头，格式为 bytes=start-end
     * @return (文件数据, http响应头)
     */
    @Override
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadPart(UUID fileId, UUID userId, String rangeHeader) {
        FileTransferConfig.DownloadConfig cfg = transferConfig.getDownloadConfig();

        return Mono.fromCallable(() -> fileSystemRpcService.getFile(userId.toString(), fileId.toString()))
            .subscribeOn(Schedulers.fromExecutor(taskExecutor))
            .switchIfEmpty(Mono.error(new FileRangeDownloadException("文件不存在或已被删除")))
            .handle((file, sink) -> {
                // 解析Range头
                ByteRange range = parseRangeHeader(rangeHeader, file.getSize());
                long length = range.end - range.start + 1;

                if (length > cfg.getMaxChunkSizeBytes()) {
                    sink.error(new FileRangeDownloadException(
                            String.format("分片大小[%d bytes]超出最大限制[%d MB]",
                                    length, cfg.getMaxChunkSizeBytes() / 1024 / 1024)
                    ));
                    return;
                }

                Flux<DataBuffer> flux = DataBufferUtils.readInputStream(
                        () -> minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(file.getBucket())
                                        .object(file.getStorageKey())
                                        .offset(range.start)
                                        .length(length)
                                        .build()
                        ),
                        new DefaultDataBufferFactory(),
                        cfg.getStreamBufferSizeBytes()
                );

                HttpStatus status = range.isPartial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
                headers.setContentLength(length);

                if (range.isPartial()) {
                    String partName = file.getName() + ".part";
                    headers.set(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", range.start(), range.end(), file.getSize()));
                    headers.setContentDisposition(ContentDisposition.attachment().filename(partName, StandardCharsets.UTF_8).build());
                } else {
                    headers.setContentType(MediaType.parseMediaType(file.getMimeType()));
                    headers.setContentDisposition(ContentDisposition.attachment().filename(file.getName(), StandardCharsets.UTF_8).build());
                }

                log.info("[downloadPart] fileId={}, filename={}, range={}-{}",
                        file.getId(), file.getName(), range.start(), range.end());

                sink.next(ResponseEntity.status(status).headers(headers).body(flux));
            });
    }

    /**
     * 将多个文件打包成 ZIP 并提供流式下载
     * @param fileIds 文件 ID 列表
     * @param userId  用户 ID
     * @return (ZIP资源, http 响应头)
     */
    public Mono<ResponseEntity<Flux<DataBuffer>>> downloadFilesAsZipArchive(List<UUID> fileIds, UUID userId) {
        FileTransferConfig.DownloadConfig cfg = transferConfig.getDownloadConfig();
        String fileKey = generateFileKey(fileIds); // fileIds 的哈希结果
        String zipName = fileKey + ".zip";
        Path zipPath = TEMP_DOWNLOAD_DIR.resolve(zipName);

        return Mono.fromCallable(() -> {
            // 如果文件已存在，直接返回（支持断点续传/重新下载）
            if (Files.exists(zipPath)) {
                return zipPath;
            }

            // 获取文件信息
            List<FileDTO> files = fileSystemRpcService.getFiles(
                    userId.toString(),
                    new GetFilesRequest(fileIds.stream().map(UUID::toString).toList())
            );

            // 总文件大小校验
            long totalSize = files.stream().map(FileDTO::getSize).reduce(0L, Long::sum);
            if(totalSize > cfg.getMaxZipBatchSizeBytes()) {
                throw new BatchZipDownloadException("批量打包的文件总大小超过最大限制");
            }

            // 将文件打包到 zip 临时文件
            try(ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
                for(FileDTO file: files) {
                    try {
                        ZipEntry entry = new ZipEntry(file.getName());
                        String filename = file.getName();

                        // 压缩文件
                        zos.setMethod(ZipOutputStream.DEFLATED);
                        if (isCompressed(filename)) { // 已压缩文件
                            zos.setLevel(Deflater.BEST_SPEED);
                        } else {
                            zos.setLevel(Deflater.BEST_COMPRESSION);
                        }
                        zos.putNextEntry(entry);

                        // 从 minio 流式读取写入 ZIP
                        try (GetObjectResponse stream = minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(file.getBucket())
                                        .object(file.getStorageKey())
                                        .build())) {
                            stream.transferTo(zos);
                        }

                        zos.closeEntry();
                    } catch (Exception e) {
                        log.error("[downloadFilesAsZipArchive] Failed to archive file, fileId={}, filename={}, bucket={}, storageKey={}, error={}",
                                file.getId(), file.getName(), file.getBucket(), file.getStorageKey(), e.getMessage());
                    }
                }
            }

            // 注册缓存
            tempFileCache.register(zipPath);

            return zipPath;
        }).subscribeOn(Schedulers.fromExecutor(taskExecutor))
        .timeout(Duration.ofMinutes(cfg.getZipPackagingTimeoutMinutes()))
        .flatMap(path -> {
            try {
                long size = Files.size(path);

                Flux<DataBuffer> flux = DataBufferUtils.read(
                        path,
                        new DefaultDataBufferFactory(),
                        cfg.getStreamBufferSizeBytes()
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType("application/zip"));
                headers.setContentDisposition(ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build()); // 弹出下载
                headers.setContentLength(size);
                headers.setCacheControl(CacheControl.noCache()); // 禁用缓存

                log.info("[downloadFilesAsZip] zipFile={}, fileCount={}, size={}", path.getFileName(), fileIds.size(), size);

                return Mono.just(ResponseEntity.ok()
                        .headers(headers)
                        .body(flux));
            } catch (IOException e) {
                log.error("[downloadFilesAsZipArchive] Failed to read temp file, path={}, error={}", path, e.getMessage());
                return Mono.error(new BatchZipDownloadException("创建ZIP下载资源失败"));
            }
        });
    }

    private record ByteRange(long start, long end, boolean isPartial) {}

    private ByteRange parseRangeHeader(String rangeHeader, long fileSize) {
        if (rangeHeader == null) {
            return new ByteRange(0, fileSize - 1, false);
        }

        if (!rangeHeader.startsWith("bytes=")) {
            throw new FileRangeDownloadException("Range请求头格式错误，必须以'bytes='开头");
        }

        String range = rangeHeader.substring(6); // 去除 'bytes='
        int dashIndex = range.indexOf('-');

        if (dashIndex == -1) {
            throw new FileRangeDownloadException("Range请求头格式错误，缺少'-'分隔符");
        }

        String startStr = range.substring(0, dashIndex);
        String endStr = range.substring(dashIndex + 1);

        long start, end;
        try {
            // -end：最后 end 字节
            if (startStr.isEmpty() && !endStr.isEmpty()) {
                long suffixLength = Long.parseLong(endStr);
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {  // start- 或 start-end
                start = Long.parseLong(startStr);
                if (start < 0) {
                    throw new FileRangeDownloadException("Range起始位置不能为负数");
                }
                end = endStr.isEmpty() ? fileSize - 1 : Long.parseLong(endStr);
            }
        } catch (NumberFormatException e) {
            throw new FileRangeDownloadException("Range参数格式错误，起始和结束位置必须为有效数字");
        }

        if (start > end || start >= fileSize) {
            throw new FileRangeDownloadException("Range范围无效：起始位置大于结尾位置或超出文件大小");
        }

        end = Math.min(end, fileSize - 1);
        return new ByteRange(start, end, true);
    }

    private String generateFileKey(List<UUID> fileIds) {
        String sortedIds = fileIds.stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","));

        return Hashing.sha256()
                .hashString(sortedIds, StandardCharsets.UTF_8)
                .toString()
                .substring(0, 16);
    }

    public static boolean isCompressed(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return false;
        }
        String ext = filename.substring(lastDotIndex + 1).toLowerCase();
        return COMPRESSED_EXTENSIONS.contains(ext);
    }
}
