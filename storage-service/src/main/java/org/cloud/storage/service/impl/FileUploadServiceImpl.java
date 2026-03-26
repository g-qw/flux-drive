package org.cloud.storage.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.messages.Part;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.cloud.api.dto.FileInputDTO;
import org.cloud.api.service.FileSystemRpcService;
import org.cloud.storage.config.minio.MinioProperties;
import org.cloud.storage.config.transfer.FileTransferConfig;
import org.cloud.storage.dto.*;
import org.cloud.storage.dto.enums.TaskType;
import org.cloud.storage.exception.AvatarUploadException;
import org.cloud.storage.exception.MultipartUploadException;
import org.cloud.storage.repository.FileProcessingTaskRepository;
import org.cloud.storage.service.FileUploadService;
import org.cloud.storage.util.StorageKeyGenerator;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {
    @DubboReference(check = false, timeout = 3000, retries = 1, lazy = true)
    private FileSystemRpcService fileSystemRpcService;

    private final FileTransferConfig transferConfig;
    private final MinioProperties minioProperties;
    private final MinioClient minioClient;
    private final MinioAsyncClient minioAsyncClient;
    private final RedissonClient redissonClient;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final FileProcessingTaskRepository fileProcessingTaskRepository;

    private static long MEMORY_THRESHOLD = 5 * 1024 * 1024; // 16 MB
    private static long STREAM_THRESHOLD = 100 * 1024 * 1024; // 100 MB
    private static Path TEMP_UPLOAD_DIR = Paths.get("tmp");
    private static final String MULTIPART_UPLOAD_METADATA_PREFIX = "multipart:metadata:";
    private static final String MULTIPART_UPLOAD_PARTS_PREFIX = "multipart:parts:";
    private static final long MULTIPART_UPLOAD_EXPIRE_HOURS = 24L;
    private static final long MAX_AVATAR_SIZE = 1024 * 1024;

    static {
        try {
            Files.createDirectories(TEMP_UPLOAD_DIR);
        } catch (IOException e) {
            // 创建失败，使用系统默认临时目录
            TEMP_UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"));
        }
    }

    public FileUploadServiceImpl(FileTransferConfig transferConfig,
                                 MinioProperties minioProperties,
                                 MinioClient minioClient,
                                 MinioAsyncClient minioAsyncClient,
                                 RedissonClient redissonClient,
                                 @Qualifier("sharedTaskExecutor") ThreadPoolTaskExecutor taskExecutor,
                                 ObjectMapper objectMapper,
                                 FileProcessingTaskRepository fileProcessingTaskRepository) {
        this.transferConfig = transferConfig;
        this.minioProperties = minioProperties;
        this.minioClient = minioClient;
        this.minioAsyncClient = minioAsyncClient;
        this.redissonClient = redissonClient;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
        MEMORY_THRESHOLD = transferConfig.getMultiFileConfig().getMemoryThreshold();
        STREAM_THRESHOLD = transferConfig.getMultiFileConfig().getStreamThreshold();
        this.fileProcessingTaskRepository = fileProcessingTaskRepository;
    }

    /**
     * 上传中小型文件
     * @param files 文件列表
     * @param fileSizes 文件大小列表
     * @param directoryId 目录 ID
     * @param userId 用户 ID
     * @return 上传结果
     */
    public Mono<List<FileUploadResult>> uploadFiles(Flux<FilePart> files, List<Long> fileSizes, UUID directoryId, UUID userId) {
        FileTransferConfig.MultiFileUploadConfig cfg = transferConfig.getMultiFileConfig();

        return files.zipWith(Flux.fromIterable(fileSizes)) // 如果数量不匹配，静默丢弃多余部分
                .onBackpressureBuffer(
                        cfg.getBackpressureBufferSize(),
                        BufferOverflowStrategy.valueOf(cfg.getOverflowStrategy())
                )
                .flatMap(tuple -> {
                        FilePart filePart = tuple.getT1();
                        Long fileSize = tuple.getT2();

                        return routeUploadBySize(filePart, fileSize, directoryId, userId)
                            .flatMap(dto -> persistFileMetadata(dto, userId)
                                .map(result -> {
                                        if(result) {
                                            return FileUploadResult.success(filePart.filename());
                                        } else {
                                            return FileUploadResult.failure(filePart.filename(), "保存文件元数据失败");
                                        }
                                    }
                                )
                            ).switchIfEmpty(Mono.just(FileUploadResult.failure(filePart.filename(), "文件上传失败")));
                    },
                    cfg.getMaxConcurrency() // 并发度
                )
                .timeout(Duration.ofMinutes(cfg.getFileTimeoutMinutes()))
                .collectList()
                .timeout(Duration.ofMinutes(cfg.getBatchTimeoutMinutes()));
    }

    /**
     * 初始化分片上传
     * @param request 初始化分片上传请求
     * @param userId 用户 ID
     * @return 上传任务ID
     */
    @SneakyThrows
    @Override
    public Mono<String> initMultipartUpload(InitMultipartUploadRequest request, UUID userId) {
        String bucket = minioProperties.getStorageBucket();
        String storageKey = StorageKeyGenerator.generateKey(userId);

        // 构造请求头
        Multimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", request.getContentType());

        return Mono.fromFuture(minioAsyncClient.createMultipartUploadAsync(bucket, null, storageKey, headers, null))
            .map(response -> response.result().uploadId())
            .flatMap(uploadId -> cacheMultipartUploadMetadata(uploadId, storageKey, request, userId))
            .doOnSuccess(uploadId -> log.info("[initMultipartUpload] uploadId={}, storageKey={}, filename={}", uploadId, storageKey, request.getFilename()))
            .onErrorResume(e -> {
                log.error("[initMultipartUpload] filename={}, size={}, error={}", request.getFilename(), request.getSize(), e.getMessage());
                return Mono.error(new MultipartUploadException("初始化文件上传失败，请稍后重试"));
            });
    }

    /**
     * 上传分片
     * @param uploadId 上传任务ID
     * @param partNumber 分片编号
     * @param filePart 文件分片数据流
     * @param userId 用户ID
     * @return 分片上传结果
     */
    @Override
    public Mono<PartUploadResult> uploadPart(String uploadId, int partNumber, Flux<DataBuffer> filePart, UUID userId) {
        FileTransferConfig.ChunkedUploadConfig cfg = transferConfig.getChunkedUploadConfig();
        String metadataKey = MULTIPART_UPLOAD_METADATA_PREFIX + userId + ":" + uploadId;
        RBucket<String> metadataBucket = redissonClient.getBucket(metadataKey);

        MultipartUploadMetadata metadata;
        try {
             metadata = objectMapper.readValue(metadataBucket.get(), MultipartUploadMetadata.class);
        } catch (Exception e) {
            log.error("[uploadPart] Failed to read multipart upload metadata, userId={}, uploadId={}, error={}", userId, uploadId, e.getMessage());
            return Mono.error(new MultipartUploadException("读取分片上传的元数据失败，上传任务可能已过期或不存在"));
        }

        return DataBufferUtils.join(filePart)
            .flatMap(buffer -> {
                long size = buffer.readableByteCount();

                // 读取分片到内存中
                byte[] bytes = new byte[(int) size];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);

                if(size > cfg.getMaxChunkSizeBytes()) {
                    return Mono.error(new MultipartUploadException("文件分片大小超过最大限制"));
                }

                return Mono.fromCallable(() -> {
                    long startTime = System.currentTimeMillis();

                    // 构造请求头
                    Multimap<String, String> headers = HashMultimap.create();
                    headers.put("Content-Type", metadata.getContentType());

                    try(InputStream is = new ByteArrayInputStream(bytes)) {
                        UploadPartResponse response = minioAsyncClient.uploadPartAsync(
                                minioProperties.getStorageBucket(),
                                null,
                                metadata.getStorageKey(),
                                is,
                                size,
                                uploadId,
                                partNumber,
                                headers,
                                null
                        ).get(30, TimeUnit.SECONDS);

                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("[uploadPart] uploadId={}, filename={}, etag={}, size={}MB, elapsed={} ms",
                                uploadId, metadata.getFilename(), response.etag(), size / 1024 / 1024, elapsed);

                        return response;
                    }
                }).timeout(Duration.ofMinutes(cfg.getChunkTimeoutMinutes()))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .onErrorMap(e -> {
                    log.error("[uploadPart] userId={}, uploadId={}, partNumber={}, filename={}, error={}",
                            userId, uploadId, partNumber, metadata.getFilename(), e.getMessage());
                    return new MultipartUploadException(String.format("文件 '%s' 的分片%s上传失败，请稍后重试", metadata.getFilename(), partNumber));
                });
            }).flatMap(response -> {
                // 缓存分片信息
                String partsListKey = MULTIPART_UPLOAD_PARTS_PREFIX + userId + ":" + uploadId;
                RList<String> partsList = redissonClient.getList(partsListKey);

                String etag = response.etag();
                String partInfo = partNumber + "-" + etag;
                partsList.add(partInfo);
                partsList.expire(Duration.ofHours(MULTIPART_UPLOAD_EXPIRE_HOURS));

                return Mono.just(new PartUploadResult(partNumber, etag));
            });
    }

    /**
     * 合并分片完成分片上传
     * @param uploadId 上传任务ID
     * @param userId 用户ID
     * @return 文件元数据输入对象
     */
    @Override
    public Mono<FileInputDTO> completeMultipartUpload(String uploadId, UUID userId) {
        String bucket = minioProperties.getStorageBucket();
        String metadataKey = MULTIPART_UPLOAD_METADATA_PREFIX + userId + ":" + uploadId;
        String partsListKey = MULTIPART_UPLOAD_PARTS_PREFIX + userId + ":" + uploadId;

        // 分片上传的元数据
        RBucket<String> metadataBucket = redissonClient.getBucket(metadataKey);
        MultipartUploadMetadata metadata;
        try {
            metadata = objectMapper.readValue(metadataBucket.get(), MultipartUploadMetadata.class);
        } catch (Exception e) {
            log.error("[completeMultipartUpload] Failed to read multipart upload metadata, userId={}, uploadId={}, error={}", userId, uploadId, e.getMessage());
            return Mono.error(new MultipartUploadException("读取分片上传元数据失败，上传任务可能已过期或不存在"));
        }

        // 分片信息
        RList<String> partsInfoList = redissonClient.getList(partsListKey);
        List<String> partsInfo = partsInfoList.readAll();

        // 构造分片信息数组并排序
        Part[] parts = partsInfo.stream()
            .map(partInfo -> {
                String[] partsArr = partInfo.split("-");
                int partNumber = Integer.parseInt(partsArr[0]);
                String eTag = partsArr[1];
                return new Part(partNumber, eTag);
            }).sorted(Comparator.comparingInt(Part::partNumber))
            .toArray(Part[]::new);

        // 构造请求头
        Multimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", metadata.getContentType());

        // 合并分片
        try {
            return Mono.fromFuture(minioAsyncClient.completeMultipartUploadAsync(bucket,null, metadata.getStorageKey(), uploadId, parts, headers, null))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .flatMap(response -> {
                    String etag = response.etag();
                    FileInputDTO file = buildFileInputDTO(
                        metadata.getStorageKey(),
                        UUID.fromString(metadata.getDirectoryId()),
                        metadata.getFilename(),
                        metadata.getContentType(),
                        etag
                    );

                    // 清理缓存
                    metadataBucket.delete();
                    partsInfoList.delete();

                    log.info("[completeMultipartUpload] uploadId={}, storageKey={}", uploadId, metadata.getStorageKey());

                    return persistFileMetadata(file, userId)
                            .flatMap(result -> {
                                if(result) {
                                    return Mono.just(file);
                                } else {
                                    return Mono.error(new MultipartUploadException("文件上传失败，请重新上传"));
                                }
                            });
                });
        } catch (Exception e) {
            log.error("[completeMultipartUpload] uploadId={}, storageKey={}, partCount={}, error={}",
                    uploadId, metadata.getStorageKey(), parts.length, e.getMessage());
            return Mono.error(new MultipartUploadException("文件合并失败，请稍后重试或重新上传"));
        }
    }

    /**
     * 上传头像文件
     * 支持格式：JPEG, PNG, GIF, WebP
     * 大小限制：< 1MB
     */
    @Override
    public Mono<String> uploadAvatar(Mono<FilePart> filePart, UUID userId) {
        final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
                "image/jpeg", "image/png", "image/gif", "image/webp"
        );

        return filePart.flatMap(part -> {
            // 检查文件大小
            if (part.headers().getContentLength() > MAX_AVATAR_SIZE) {
                return Mono.error(new AvatarUploadException(String.format("头像文件大小不能超过 %s MB", MAX_AVATAR_SIZE / 1024 / 1024)));
            }

            // 检查文件类型
            String contentType = String.valueOf(part.headers().getContentType());
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
                return Mono.error(new AvatarUploadException("无效的图像格式，支持的格式包括 JPEG, PNG, GIF, WEBP"));
            }

            // 生成存储路径
            String extension = getExtensionFromContentType(contentType);
            String storageKey = StorageKeyGenerator.generateKey(userId) + "." + extension;

            // 读取文件内容并上传
            return DataBufferUtils.join(part.content()) // 加载到内存中
                .flatMap(buffer -> {
                    int size = buffer.readableByteCount();

                    return Mono.fromCallable(() -> {
                        try (InputStream stream = buffer.asInputStream(true)) {
                            ObjectWriteResponse resp = minioClient.putObject(
                                    PutObjectArgs.builder()
                                            .bucket(minioProperties.getAvatarBucket())
                                            .object(storageKey)
                                            .stream(stream, size, -1)  // 用 buffer 的长度
                                            .contentType(contentType)
                                            .build()
                            );

                            log.info("[uploadAvatar] bucket={}, storageKey={}, filename={}, userId={}, size={} bytes, etag={}",
                                    minioProperties.getAvatarBucket(), storageKey, part.filename(), userId, size, resp.etag());

                            return String.format("%s/%s/%s", minioProperties.getBaseUrl(), minioProperties.getAvatarBucket(), storageKey);
                        }
                    }).subscribeOn(Schedulers.fromExecutor(taskExecutor));
                });

        });
    }

    /**
     * 根据ContentType获取文件扩展名
     */
    private String getExtensionFromContentType(String contentType) {
        switch (contentType) {
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            default: return "jpg";
        }
    }

    /**
     * 智能文件上传路由 - 根据文件大小自动选择最优上传策略
     */
    private Mono<FileInputDTO> routeUploadBySize(FilePart filePart, long fileSize, UUID directoryId, UUID userId) {
        long startTime = System.currentTimeMillis();

        if(fileSize > 0) {
            if (fileSize <= MEMORY_THRESHOLD) { // 小文件
                return memoryUpload(filePart, directoryId, userId)
                    .doOnSuccess(dto -> {
                        String size = String.format("%.2f", dto.getSize() / 1024.0);
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("[memoryUpload] bucket={}, storageKey={}, filename={}, directoryId={}, userId={}, size={} KB, elapsed={} ms",
                                dto.getBucket(), dto.getStorageKey(), dto.getName(), dto.getDirectoryId(), userId, size, elapsed);
                    })
                    .onErrorResume(e -> {
                        log.error("[memoryUpload] filename={}, directoryId={}, userId={}, error={}",
                                filePart.filename(), directoryId, userId, e.getMessage());
                        return Mono.empty();
                    });
            } else if (fileSize <= STREAM_THRESHOLD) { // 中等文件
                return streamingUpload(filePart, directoryId, userId)
                    .doOnSuccess(dto -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.info("[streamingUpload] bucket={}, storageKey={}, filename={}, directoryId={}, userId={}, size={} MB, elapsed={}ms",
                                dto.getBucket(), dto.getStorageKey(), dto.getName(), dto.getDirectoryId(), userId, dto.getSize() / 1024 / 1024, elapsed);
                    })
                    .onErrorResume(e -> {
                        log.error("[streamingUpload] filename={}, directoryId={}, userId={}, error={}",
                                filePart.filename(), directoryId, userId, e.getMessage());
                        return Mono.empty();
                    });
            } else {
                log.warn("[routeUploadBySize] SKIPPED: filename={}, size={} bytes exceeds limit. Max allowed={} bytes",
                        filePart.name(), fileSize, STREAM_THRESHOLD);
                return Mono.empty();
            }
        }

        return Mono.empty(); // 文件大小未知，直接跳过
    }

    /**
     * 小文件内存直传（低延迟）
     *
     * @param filePart    文件片段（Multipart）
     * @param directoryId 目录ID
     * @param userId      用户ID
     * @return 上传结果
     */
    private Mono<FileInputDTO> memoryUpload(FilePart filePart, UUID directoryId, UUID userId) {
        String contentType = resolveContentType(filePart);
        String storageKey = StorageKeyGenerator.generateKey(userId);

        return DataBufferUtils.join(filePart.content()) // 加载到内存中
            .flatMap(buffer -> {
                int size = buffer.readableByteCount();

                return Mono.fromCallable(() -> {
                    try (InputStream stream = buffer.asInputStream(true)) {
                        ObjectWriteResponse resp = minioClient.putObject(
                            PutObjectArgs.builder()
                                .bucket(minioProperties.getStorageBucket())
                                .object(storageKey)
                                .stream(stream, size, -1)  // 用 buffer 的长度
                                .contentType(contentType)
                                .build()
                        );

                        return buildFileInputDTO(storageKey, directoryId, filePart.filename(), contentType, resp.etag());
                    }
                }).subscribeOn(Schedulers.fromExecutor(taskExecutor));
            });
    }

    /**
     * 中等文件管道流式上传
     *  @param filePart    文件片段（Multipart）
     *  @param directoryId 目录ID
     *  @param userId      用户ID
     */
    private Mono<FileInputDTO> streamingUpload(FilePart filePart, UUID directoryId, UUID userId) {
        FileTransferConfig.MultiFileUploadConfig cfg = transferConfig.getMultiFileConfig();
        String bucket = minioProperties.getStorageBucket();
        String contentType = resolveContentType(filePart);
        String storageKey = StorageKeyGenerator.generateKey(userId);
        long partSize = 5 * 1024 * 1024;
        int bufferSize = cfg.getStreamBufferSize();

        return Mono.using(
                () -> createPipedStreamBridge(filePart, bufferSize),
                inputStream -> Mono.fromCallable(() -> {
                    ObjectWriteResponse response = minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucket)
                                    .object(storageKey)
                                    .stream(inputStream, -1, partSize)
                                    .contentType(contentType)
                                    .build()
                    );
                    return buildFileInputDTO(storageKey, directoryId, filePart.filename(), contentType, response.etag());
                }).subscribeOn(Schedulers.fromExecutor(taskExecutor)),
                this::closeQuietly
        );
    }

    private PipedInputStream createPipedStreamBridge(FilePart filePart, int bufferSize) throws IOException {
        PipedInputStream pis = new PipedInputStream(bufferSize);
        PipedOutputStream pos = new PipedOutputStream(pis);

        filePart.content()
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .subscribe(
                        buffer -> writeBufferToStream(buffer, pos),
                        error -> closeQuietly(pos),
                        () -> closeQuietly(pos)
                );

        return pis;
    }

    private void writeBufferToStream(DataBuffer buffer, PipedOutputStream stream) {
        try (InputStream is = buffer.asInputStream()) {
            is.transferTo(stream); // Java 9+，自动高效传输
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) closeable.close();
        } catch (IOException ignored) {}
    }

    /**
     * 持久化文件元数据到文件系统服务（RPC调用）
     */
    private Mono<Boolean> persistFileMetadata(FileInputDTO file, UUID userId) {
        return Mono.fromCallable(() -> fileSystemRpcService.addFile(userId.toString(), file))
                .subscribeOn(Schedulers.fromExecutor(taskExecutor))
                .doOnSuccess(dto -> {
                    String mimeType = file.getMimeType();
                    TaskType taskType = null;
                    if(mimeType.startsWith("image/")) { // 图片类型
                        taskType = TaskType.THUMBNAIL;
                    } else if(mimeType.startsWith("video/")) { // 视频类型
                        taskType = TaskType.GIF;
                    }

                    if(taskType != null) {
                        try {
                            fileProcessingTaskRepository.create(
                                new FileProcessingTaskInput.Builder()
                                    .fileId(UUID.fromString(dto.getId()))
                                    .userId(userId)
                                    .md5(dto.getMd5())
                                    .bucket(dto.getBucket())
                                    .storageKey(dto.getStorageKey())
                                    .taskType(taskType.getCode())
                                    .build()
                            );
                        } catch(Exception e) {
                            log.error("[persistFileMetadata] Failed to create processing task, fileId={}, taskType={}, error={}",
                                    dto.getId(), taskType, e.getMessage());
                        }
                    }
                })
                .map(result -> true)
                .onErrorResume(
                      e-> {
                          log.error("[notifyFileSystemRpc] RPC notification failed, filename={}, bucket={}, storageKey={}, error={}",
                                  file.getName(), file.getBucket(), file.getStorageKey(), e.getMessage());

                          try {
                              minioClient.removeObject(
                                      RemoveObjectArgs.builder()
                                              .bucket(file.getBucket())
                                              .object(file.getStorageKey())
                                              .build()
                              );
                          } catch (Exception cleanupEx) {
                              log.error("[notifyFileSystemRpc] Cleanup failed, bucket={}, storageKey={}, error={}",
                                      file.getBucket(), file.getStorageKey(), cleanupEx.getMessage());
                          }
                          return Mono.just(false);
                      }
                );
    }

    private String resolveContentType(FilePart filePart) {
        return Optional.ofNullable(filePart.headers().getContentType())
                .map(MimeType::toString)
                .orElse("application/octet-stream");
    }

    private FileInputDTO buildFileInputDTO(String storageKey, UUID directoryId,  String filename, String contentType, String etag) {
        /*
         处理 minio 返回的 etag
         1. 单文件上传无分片: MD5
         2. 单文件上传有分片: "MD5-N", N 为分片数量
         3. 分片上传：MD5
         */
        String md5 = etag;
        if(md5 != null) {
            if (md5.startsWith("\"") && md5.endsWith("\"")) {
                md5 = md5.substring(1, md5.length() - 1);  // 去掉首尾双引号
            }

            if (md5.contains("-")) {
                md5 = md5.substring(0, md5.lastIndexOf('-'));  // 去掉 -N 后缀
            }
        }

        long size = getObjectSize(minioProperties.getStorageBucket(), storageKey);

        return FileInputDTO.builder()
                .directoryId(directoryId.toString())
                .bucket(minioProperties.getStorageBucket())
                .storageKey(storageKey)
                .name(filename)
                .mimeType(contentType)
                .size(size)
                .md5(md5)
                .build();
    }

    private long getObjectSize(String bucket, String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return stat.size();
        } catch (Exception e) {
            log.warn("Failed to get object size from MinIO, key: {}", objectKey, e);
            return -1;
        }
    }

    /**
     * 缓存分片上传任务元数据到Redis
     */
    private Mono<String> cacheMultipartUploadMetadata(String uploadId, String storageKey, InitMultipartUploadRequest request, UUID userId) {

        String metadataKey = MULTIPART_UPLOAD_METADATA_PREFIX + userId + ":" + uploadId;
        RBucket<String> bucket = redissonClient.getBucket(metadataKey);

        // 构建元数据
        MultipartUploadMetadata metadata = MultipartUploadMetadata.builder()
                .directoryId(request.getDirectoryId().toString())
                .storageKey(storageKey)
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .fileSize(request.getSize())
                .createdAt(System.currentTimeMillis())
                .build();

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("[cacheMultipartUploadMetadata] uploadId={}, storageKey={}, request={}, error={}", uploadId, storageKey, request, e.getMessage());
            return Mono.error(e);
        }

        return Mono.fromRunnable(() -> {
                    bucket.set(metadataJson);
                    bucket.expire(Duration.ofHours(MULTIPART_UPLOAD_EXPIRE_HOURS));
                })
                .thenReturn(uploadId);
    }
}
